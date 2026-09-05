#!/usr/bin/env python3
"""Ponte Hermes-chat: 127.0.0.1:9120 -> 127.0.0.1:8642 (gateway OpenAI-compat).
Injeta API_SERVER_KEY do .env do Hermes. Serve /profile (nome do agente via
SOUL.md + config) e /profiles + /profile/select (perfis Hermes).
Faz proxy transparente de streaming (SSE) e encaminha para o perfil correto
(/p/<perfil>/v1/...) quando não for o default.
"""
import http.client
import json
import os
import re
import urllib.request
import urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERMES_HOME = "/root/.hermes"
GATEWAY_HOST = "127.0.0.1"
GATEWAY_PORT = 8642
LISTEN = ("127.0.0.1", 9120)

PROFILE_FILE = os.path.join(HERMES_HOME, ".hermes_chat_profile")
MODEL_FILE = os.path.join(HERMES_HOME, ".hermes_chat_model")

DASHBOARD_HOST = "127.0.0.1"
DASHBOARD_PORT = 9119
_DASH_TOKEN_CACHE = {"token": None, "ts": 0.0}


def _dashboard_token():
    import time
    now = time.time()
    if _DASH_TOKEN_CACHE["token"] and now - _DASH_TOKEN_CACHE["ts"] < 30:
        return _DASH_TOKEN_CACHE["token"]
    try:
        req = urllib.request.Request(
            f"http://{DASHBOARD_HOST}:{DASHBOARD_PORT}/",
            headers={"User-Agent": "hermes-chat-bridge"},
        )
        with urllib.request.urlopen(req, timeout=8) as r:
            html = r.read().decode("utf-8", "replace")
        m = re.search(r'__HERMES_SESSION_TOKEN__\s*=\s*"([^"]+)"', html)
        if not m:
            return None
        _DASH_TOKEN_CACHE["token"] = m.group(1)
        _DASH_TOKEN_CACHE["ts"] = now
        return m.group(1)
    except Exception:
        return None


def dashboard(path, timeout=12):
    token = _dashboard_token()
    if not token:
        return 503, {
            "error": "dashboard_token_unavailable",
            "detail": "Não consegui autenticar no dashboard (9119)."
        }
    try:
        req = urllib.request.Request(
            f"http://{DASHBOARD_HOST}:{DASHBOARD_PORT}{path}",
            headers={
                "X-Hermes-Session-Token": token,
                "User-Agent": "hermes-chat-bridge"
            },
        )
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8", "replace"))
        except Exception:
            return e.code, {"detail": str(e)}
    except Exception as e:
        return 502, {"error": "dashboard_unreachable", "detail": str(e)}


def load_api_key():
    k = os.environ.get("API_SERVER_KEY", "").strip()
    if k:
        return k
    try:
        with open(os.path.join(HERMES_HOME, ".env")) as f:
            for line in f:
                if line.startswith("API_SERVER_KEY="):
                    return line.split("=", 1)[1].strip()
    except OSError:
        pass
    return ""


API_KEY = load_api_key()


def _profile_name_from_folder(folder, default_name):
    soul = os.path.join(folder, "SOUL.md")
    if os.path.isfile(soul):
        try:
            with open(soul) as f:
                for line in f:
                    s = line.strip()
                    if s.lower().startswith("you are "):
                        rest = s[len("you are "):].strip()
                        return rest.split(",")[0].split(".")[0].strip()
                    if s.startswith("# "):
                        return s[2:].strip()
        except OSError:
            pass
    return default_name


def selected_profile():
    # 1. Variável de ambiente (se definida no Termux / shell)
    env_p = os.environ.get("HERMES_PROFILE", "").strip()
    if env_p and (env_p == "default" or os.path.isdir(os.path.join(HERMES_HOME, "profiles", env_p))):
        return env_p

    # 2. Ficheiros conhecidos de perfil activo do Hermes CLI e da ponte
    candidates = [
        PROFILE_FILE,
        os.path.join(HERMES_HOME, ".active_profile"),
        os.path.join(HERMES_HOME, "active_profile"),
        os.path.join(HERMES_HOME, ".hermes_profile"),
        os.path.join(HERMES_HOME, ".profile"),
        os.path.join(HERMES_HOME, "current_profile"),
        os.path.join(HERMES_HOME, ".current_profile"),
    ]
    for cand in candidates:
        try:
            with open(cand, "r", encoding="utf-8", errors="replace") as f:
                p = f.read().strip()
                if p and (p == "default" or os.path.isdir(os.path.join(HERMES_HOME, "profiles", p))):
                    return p
        except OSError:
            pass

    return "default"


def _clean_model_name(val):
    if not val:
        return ""
    s = val.strip().strip("'\"")
    if not s or s.lower() in ("hermes-agent", "hermes"):
        return ""
    return s


def selected_model():
    p = selected_profile()
    home = _home_for(p)
    # Primeiro verifica ficheiro de modelo específico do perfil
    for cand in [
        os.path.join(home, ".hermes_chat_model"),
        MODEL_FILE
    ]:
        try:
            with open(cand, "r", encoding="utf-8", errors="replace") as f:
                m = _clean_model_name(f.read())
                if m:
                    return m
        except OSError:
            pass
    return ""


def _home_for(profile):
    if profile == "default":
        return HERMES_HOME
    return os.path.join(HERMES_HOME, "profiles", profile)


def agent_name():
    p = selected_profile()
    fallback = "Agent T" if p == "default" else p.capitalize()
    return _profile_name_from_folder(_home_for(p), fallback)


def _model_default(home):
    # 1. Override explícito no perfil se foi escolhido pelo utilizador
    prof_model_file = os.path.join(home, ".hermes_chat_model")
    try:
        with open(prof_model_file, "r", encoding="utf-8", errors="replace") as f:
            m = _clean_model_name(f.read())
            if m:
                return m
    except OSError:
        pass

    # 2. Leitura e parsing do config.yaml do perfil
    cfg_file = os.path.join(home, "config.yaml")
    try:
        with open(cfg_file, "r", encoding="utf-8", errors="replace") as f:
            lines = f.readlines()
    except OSError:
        return ""

    in_model_block = False
    model_block_indent = -1
    for line in lines:
        raw = line.rstrip("\r\n")
        stripped = raw.strip()
        if not stripped or stripped.startswith("#"):
            continue
        indent = len(raw) - len(raw.lstrip())

        if in_model_block:
            if indent <= model_block_indent:
                in_model_block = False
            else:
                if stripped.startswith("default:"):
                    parts = stripped.split(":", 1)
                    val = _clean_model_name(parts[1])
                    if val:
                        return val
                elif stripped.startswith("name:"):
                    parts = stripped.split(":", 1)
                    val = _clean_model_name(parts[1])
                    if val:
                        return val

        if stripped.startswith("model:"):
            parts = stripped.split(":", 1)
            after = parts[1].strip()
            if after and not after.startswith("{"):
                val = _clean_model_name(after)
                if val:
                    return val
            in_model_block = True
            model_block_indent = indent

    # Fallback por regex caso o ficheiro use outro formato
    txt = "\n".join(lines)
    m = re.search(r"default:\s*['\"]?([a-zA-Z0-9_\-\./]+)['\"]?", txt)
    if m:
        val = _clean_model_name(m.group(1))
        if val:
            return val
    m = re.search(r"(?:^|\n)\s*model:\s*['\"]?([a-zA-Z0-9_\-\./]+)['\"]?", txt)
    if m:
        val = _clean_model_name(m.group(1))
        if val:
            return val

    return ""


def set_active_model(model):
    if not model:
        return
    model = _clean_model_name(model)
    if not model:
        return
    prof = selected_profile()
    home = _home_for(prof)
    cfg_file = os.path.join(home, "config.yaml")
    if os.path.isfile(cfg_file):
        try:
            with open(cfg_file, "r", encoding="utf-8", errors="replace") as f:
                content = f.read()
            if re.search(r"default:\s*", content):
                new_content = re.sub(
                    r"(default:\s*)['\"]?[^'\"\n\r]+['\"]?",
                    r"\g<1>" + model,
                    content,
                    count=1
                )
            elif re.search(r"^model:\s*", content, re.M):
                new_content = re.sub(
                    r"(^model:\s*)['\"]?[^'\"\n\r]+['\"]?",
                    r"\g<1>" + model,
                    content,
                    count=1,
                    flags=re.M
                )
            else:
                new_content = content + f"\nmodel:\n  default: {model}\n"
            with open(cfg_file, "w", encoding="utf-8") as f:
                f.write(new_content)
        except Exception:
            pass

    # Guarda modelo no perfil e no global
    try:
        with open(os.path.join(home, ".hermes_chat_model"), "w", encoding="utf-8") as f:
            f.write(model)
        with open(MODEL_FILE, "w", encoding="utf-8") as f:
            f.write(model)
    except OSError:
        pass


def set_active_profile(prof):
    if prof != "default" and not os.path.isdir(os.path.join(HERMES_HOME, "profiles", prof)):
        return False, None, None

    # Guarda o perfil ativo no ficheiro da ponte e nos ficheiros do CLI
    with open(PROFILE_FILE, "w", encoding="utf-8") as f:
        f.write(prof)
    for alt in [
        os.path.join(HERMES_HOME, ".active_profile"),
        os.path.join(HERMES_HOME, "active_profile")
    ]:
        try:
            with open(alt, "w", encoding="utf-8") as f:
                f.write(prof)
        except OSError:
            pass

    # Sincroniza via CLI do Hermes se o binário estiver no path
    try:
        import subprocess
        subprocess.run(
            ["hermes", "profile", "use", prof],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=3,
        )
    except Exception:
        pass

    # Ao mudar de perfil, atualiza o modelo ativo para ser o modelo DESSE perfil!
    new_home = _home_for(prof)
    prof_model = _model_default(new_home)
    if prof_model:
        try:
            with open(MODEL_FILE, "w", encoding="utf-8") as f:
                f.write(prof_model)
        except OSError:
            pass
    else:
        # Remove override stale para não herdar o modelo do perfil anterior
        try:
            if os.path.isfile(MODEL_FILE):
                os.remove(MODEL_FILE)
        except OSError:
            pass

    return True, agent_name(), agent_model()


def agent_model():
    p = selected_profile()
    home = _home_for(p)
    # 1. Modelo configurado para o perfil atual
    mod = _model_default(home)
    if mod:
        return mod
    # 2. Override do ficheiro global se existir
    sm = selected_model()
    if sm:
        return sm
    # 3. Fallback para o config default
    if p != "default":
        def_mod = _model_default(HERMES_HOME)
        if def_mod:
            return def_mod
    return "nousresearch/hermes-3-llama-3.1-8b"


def list_all_profiles():
    profiles_dir = os.path.join(HERMES_HOME, "profiles")
    active_profile = selected_profile()

    results = [{
        "id": "default",
        "name": _profile_name_from_folder(HERMES_HOME, "Agent T"),
        "active": (active_profile == "default"),
        "model": _model_default(HERMES_HOME),
    }]
    if os.path.isdir(profiles_dir):
        for d in sorted(os.listdir(profiles_dir)):
            p = os.path.join(profiles_dir, d)
            if os.path.isdir(p):
                results.append({
                    "id": d,
                    "name": _profile_name_from_folder(p, d.capitalize()),
                    "active": (active_profile == d),
                    "model": _model_default(p),
                })
    return {"current": active_profile, "profiles": results}


def list_all_models():
    models_list = []

    def add_model(m):
        clean = (m or "").strip()
        if not clean or clean.lower() in ("hermes-agent", "hermes"):
            return
        if clean not in models_list:
            models_list.append(clean)

    cur_model = agent_model()
    if cur_model:
        add_model(cur_model)

    configs = [os.path.join(HERMES_HOME, "config.yaml")]
    profiles_dir = os.path.join(HERMES_HOME, "profiles")
    if os.path.isdir(profiles_dir):
        for d in sorted(os.listdir(profiles_dir)):
            cfg = os.path.join(profiles_dir, d, "config.yaml")
            if os.path.isfile(cfg):
                configs.append(cfg)

    for cfg in configs:
        try:
            with open(cfg) as f:
                txt = f.read()
            for line in txt.splitlines():
                stripped = line.strip()
                if not stripped or stripped.startswith("#"):
                    continue
                if stripped.startswith("default:"):
                    parts = stripped.split(":", 1)
                    if len(parts) > 1:
                        add_model(parts[1].strip().strip("'\""))
                elif stripped.endswith(": {}") or stripped.endswith(":"):
                    cand = stripped.split(":", 1)[0].strip().strip("'\"")
                    if "/" in cand or cand.startswith("gemini-") or cand.startswith("qwen") or cand.startswith("gemma-"):
                        add_model(cand)
        except OSError:
            pass

    try:
        req = urllib.request.Request(
            f"http://{GATEWAY_HOST}:{GATEWAY_PORT}/v1/models",
            headers={"User-Agent": "hermes-chat-bridge"}
        )
        if API_KEY:
            req.add_header("Authorization", f"Bearer {API_KEY}")
        with urllib.request.urlopen(req, timeout=3) as r:
            if r.status == 200:
                data = json.loads(r.read().decode("utf-8", "replace"))
                items = data.get("data", []) if isinstance(data, dict) else (data if isinstance(data, list) else [])
                for it in items:
                    mid = it.get("id") if isinstance(it, dict) else str(it)
                    add_model(mid)
    except Exception:
        pass

    default_catalog = [
        "nousresearch/hermes-3-llama-3.1-8b",
        "nousresearch/hermes-3-llama-3.1-70b",
        "meta-llama/llama-3.3-70b-instruct",
        "meta-llama/llama-3.1-8b-instruct",
        "qwen/qwen-2.5-72b-instruct",
        "qwen/qwen-2.5-coder-32b-instruct",
        "deepseek/deepseek-chat",
        "deepseek/deepseek-r1",
        "mistralai/mistral-large-2407",
        "openai/gpt-4o",
        "openai/gpt-4o-mini",
        "anthropic/claude-3-5-sonnet"
    ]
    for dm in default_catalog:
        add_model(dm)

    return {
        "object": "list",
        "data": [{"id": m, "object": "model"} for m in models_list]
    }


def _send_json(handler, payload, status=200):
    data = json.dumps(payload, ensure_ascii=False).encode()
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json")
    handler.send_header("Content-Length", str(len(data)))
    handler.end_headers()
    handler.wfile.write(data)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _connect_upstream(self, body):
        headers = {
            k: v
            for k, v in self.headers.items()
            if k.lower() not in ("host", "content-length", "authorization", "connection")
        }
        headers["Content-Type"] = "application/json"
        if API_KEY:
            headers["Authorization"] = "Bearer " + API_KEY

        path = self.path
        prof = selected_profile()
        if prof != "default":
            stripped = path.lstrip("/")
            if stripped.startswith("v1/"):
                path = f"/p/{prof}/{stripped}"

        sm = selected_model()
        if sm and body and b'"messages"' in body:
            try:
                data = json.loads(body.decode("utf-8", "replace"))
                if isinstance(data, dict):
                    data["model"] = sm
                    body = json.dumps(data).encode("utf-8")
            except Exception:
                pass

        conn = http.client.HTTPConnection(GATEWAY_HOST, GATEWAY_PORT, timeout=600)
        conn.request(self.command, path, body=body, headers=headers)
        return conn, conn.getresponse()

    def _relay(self, body=None):
        try:
            conn, resp = self._connect_upstream(body)
        except Exception as e:
            _send_json(self, {"error": {"message": str(e)}}, status=502)
            return

        ctype = resp.getheader("Content-Type") or ""
        is_stream = (
            "text/event-stream" in ctype
            or (body and body.lstrip().startswith(b'{') and b'"stream": true' in body)
        )

        self.send_response(resp.status)
        for key, val in resp.getheaders():
            if key.lower() in ("transfer-encoding", "connection", "keep-alive", "content-length"):
                continue
            self.send_header(key, val)

        if is_stream:
            self.send_header("Connection", "close")
            self.send_header("Cache-Control", "no-cache, no-transform")
            self.send_header("X-Accel-Buffering", "no")
            self.end_headers()
            try:
                while True:
                    line = resp.readline()
                    if not line:
                        break
                    self.wfile.write(line)
                    self.wfile.flush()
            except Exception:
                pass
            finally:
                try:
                    self.wfile.flush()
                    self.close_connection = True
                    conn.close()
                except Exception:
                    pass
            return

        data = resp.read()
        conn.close()
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path.rstrip("/") == "/profile":
            _send_json(self, {
                "id": selected_profile(),
                "profile": selected_profile(),
                "name": agent_name(),
                "model": agent_model()
            })
            return
        if self.path.rstrip("/") in ("/profiles", "/api/profiles"):
            _send_json(self, list_all_profiles())
            return
        if self.path.rstrip("/") in ("/models", "/v1/models", "/api/models"):
            _send_json(self, list_all_models())
            return

        if self.path.rstrip("/") == "/dashboard/status":
            st, payload = dashboard("/api/status", timeout=8)
            _send_json(self, payload, status=st)
            return
        if self.path.rstrip("/") == "/dashboard/sessions":
            st, payload = dashboard("/api/sessions?limit=20")
            _send_json(self, payload, status=st)
            return
        if self.path.rstrip("/") == "/dashboard/analytics":
            st, payload = dashboard("/api/analytics/usage?days=30")
            _send_json(self, payload, status=st)
            return

        self._relay()

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else None
        if self.path.rstrip("/") in ("/model/select", "/model"):
            try:
                data = json.loads((body or b"{}").decode())
                model = data.get("model", "").strip()
                if model:
                    set_active_model(model)
                _send_json(self, {"success": True, "model": agent_model()})
            except Exception as e:
                _send_json(self, {"success": False, "error": str(e)}, status=400)
            return
        if self.path.rstrip("/") == "/profile/select":
            try:
                data = json.loads((body or b"{}").decode())
                prof = data.get("profile", "default")
                ok, name, model = set_active_profile(prof)
                if not ok:
                    _send_json(self, {"success": False, "error": f"unknown profile: {prof}"}, status=404)
                    return
                _send_json(self, {
                    "success": True,
                    "profile": prof,
                    "name": name,
                    "model": model
                })
            except Exception as e:
                _send_json(self, {"success": False, "error": str(e)}, status=400)
            return
        self._relay(body)

    do_PUT = do_POST
    do_DELETE = do_POST

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    print(f"hermes_chat_bridge a escutar em {LISTEN[0]}:{LISTEN[1]} -> "
          f"{GATEWAY_HOST}:{GATEWAY_PORT} (auth: {'OK' if API_KEY else 'SEM CHAVE!'})", flush=True)
    ThreadingHTTPServer(LISTEN, Handler).serve_forever()
