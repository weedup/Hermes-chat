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
    try:
        with open(PROFILE_FILE) as f:
            p = f.read().strip()
    except OSError:
        return "default"
    if p and (p == "default" or os.path.isdir(os.path.join(HERMES_HOME, "profiles", p))):
        return p
    return "default"


def selected_model():
    try:
        with open(MODEL_FILE) as f:
            m = f.read().strip()
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
    try:
        with open(os.path.join(home, "config.yaml")) as f:
            txt = f.read()
    except OSError:
        return ""
    m = re.search(r"^model:[ \t]*$\n((?:[ \t]+[^\n]*\n?)*)", txt, re.M)
    if m:
        dm = re.search(r"^[ \t]+default:[ \t]*(\S+)", m.group(1), re.M)
        if dm:
            return dm.group(1)
    return ""


def set_active_model(model):
    if not model:
        return
    prof = selected_profile()
    home = _home_for(prof)
    cfg_file = os.path.join(home, "config.yaml")
    if os.path.isfile(cfg_file):
        try:
            with open(cfg_file, "r") as f:
                content = f.read()
            new_content = re.sub(
                r"(^model:\s*\n(?:\s+[^\n]*\n)*?\s+default:\s*)[^\n]+",
                r"\g<1>" + model,
                content,
                flags=re.M
            )
            with open(cfg_file, "w") as f:
                f.write(new_content)
        except Exception:
            pass
    with open(MODEL_FILE, "w") as f:
        f.write(model)


def agent_model():
    sm = selected_model()
    if sm:
        return sm
    return _model_default(_home_for(selected_profile()))


def list_all_profiles():
    profiles_dir = os.path.join(HERMES_HOME, "profiles")
    active_profile = selected_profile()

    results = [{
        "id": "default",
        "name": _profile_name_from_folder(HERMES_HOME, "Agent T"),
        "active": (active_profile == "default"),
    }]
    if os.path.isdir(profiles_dir):
        for d in sorted(os.listdir(profiles_dir)):
            p = os.path.join(profiles_dir, d)
            if os.path.isdir(p):
                results.append({
                    "id": d,
                    "name": _profile_name_from_folder(p, d.capitalize()),
                    "active": (active_profile == d),
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
        if self.path == "/profile":
            _send_json(self, {"name": agent_name(), "model": agent_model()})
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
                _send_json(self, {"success": True, "model": model})
            except Exception as e:
                _send_json(self, {"success": False, "error": str(e)}, status=400)
            return
        if self.path.rstrip("/") == "/profile/select":
            try:
                data = json.loads((body or b"{}").decode())
                prof = data.get("profile", "default")
                if prof != "default" and not os.path.isdir(os.path.join(HERMES_HOME, "profiles", prof)):
                    _send_json(self, {"success": False, "error": f"unknown profile: {prof}"}, status=404)
                    return
                with open(PROFILE_FILE, "w") as f:
                    f.write(prof)
                _send_json(self, {"success": True, "profile": prof})
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
