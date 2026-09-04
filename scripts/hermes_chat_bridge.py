#!/usr/bin/env python3
"""Ponte Hermes-chat: 127.0.0.1:9120 → 127.0.0.1:8642 (gateway OpenAI-compat).
Injeta API_SERVER_KEY do .env do Hermes. Serve /profile (nome do agente via
SOUL.md + custom_providers) e /profiles + /profile/select (perfis Hermes).
Retransmite respostas streaming (SSE) chunk-a-chunk."""
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

# Dashboard Hermes (admin surface): 9119 em loopback. /api/status é público;
# os endpoints sensíveis (sessions, cron, skills, analytics…) exigem o token de
# sessão efémero que o SPA recebe via window.__HERMES_SESSION_TOKEN__ em `/`.
DASHBOARD_HOST = "127.0.0.1"
DASHBOARD_PORT = 9119
_DASH_TOKEN_CACHE = {"token": None, "ts": 0.0}


def _dashboard_token():
    """Busca o token de sessão do dashboard, com cache curta (efémero — morre
    quando o dashboard reinicia, por isso não se pode confiar numa cache longa)."""
    now = __import__("time").time()
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
    except Exception:  # noqa: BLE001
        return None


def dashboard(path, timeout=12):
    """Proxy sensível para o dashboard. Devolve (status, payload-dict)."""
    token = _dashboard_token()
    if not token:
        return 503, {"error": "dashboard_token_unavailable",
                     "detail": "Não consegui autenticar no dashboard (9119)."}
    try:
        req = urllib.request.Request(
            f"http://{DASHBOARD_HOST}:{DASHBOARD_PORT}{path}",
            headers={"X-Hermes-Session-Token": token,
                     "User-Agent": "hermes-chat-bridge"},
        )
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8", "replace"))
        except Exception:  # noqa: BLE001
            return e.code, {"detail": str(e)}
    except Exception as e:  # noqa: BLE001
        return 502, {"error": "dashboard_unreachable", "detail": str(e)}


def load_api_key():
    # env primeiro, depois .env do hermes
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


def agent_name():
    return _profile_name_from_folder(HERMES_HOME, "Hermes")


def agent_model():
    try:
        with open(os.path.join(HERMES_HOME, "config.yaml")) as f:
            txt = f.read()
        # primeiro model: depois do custom_providers
        m = re.search(r"custom_providers:.*?model:\s*(\S+)", txt, re.S)
        if m:
            return m.group(1)
        m = re.search(r"^\s*model:\s*(\S+)", txt, re.M)
        if m:
            return m.group(1)
    except OSError:
        pass
    return ""


def list_all_profiles():
    profiles_dir = os.path.join(HERMES_HOME, "profiles")
    active_profile = os.environ.get("HERMES_PROFILE") or "default"

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
    """Descobre os modelos configurados no Hermes e no gateway upstream."""
    models_set = set()

    # 1. Tentar ler modelos do gateway upstream (8642) se disponível
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
                    if mid and mid.lower() not in ("hermes-agent", "hermes"):
                        models_set.add(mid)
    except Exception:
        pass

    # 2. Ler config.yaml do hermes e dos perfis
    configs = [os.path.join(HERMES_HOME, "config.yaml")]
    profiles_dir = os.path.join(HERMES_HOME, "profiles")
    if os.path.isdir(profiles_dir):
        for d in os.listdir(profiles_dir):
            cfg = os.path.join(profiles_dir, d, "config.yaml")
            if os.path.isfile(cfg):
                configs.append(cfg)

    for cfg in configs:
        try:
            with open(cfg) as f:
                txt = f.read()
            for m in re.findall(r"model:\s*(\S+)", txt):
                clean = m.strip().strip("'\"")
                if clean and clean.lower() not in ("hermes-agent", "hermes"):
                    models_set.add(clean)
        except OSError:
            pass

    # 3. Modelos populares conhecidos para complementar a lista
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

    result_list = list(models_set)
    for dm in default_catalog:
        if dm not in result_list:
            result_list.append(dm)

    return {
        "object": "list",
        "data": [{"id": m, "object": "model"} for m in result_list]
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
        conn = http.client.HTTPConnection(GATEWAY_HOST, GATEWAY_PORT, timeout=600)
        conn.request(self.command, self.path, body=body, headers=headers)
        return conn, conn.getresponse()

    def _relay(self, body=None):
        try:
            conn, resp = self._connect_upstream(body)
        except Exception as e:  # noqa: BLE001
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
            # Streaming: retransmite cada linha/chunk SSE à medida que chega do gateway
            # sem bufferizar (readline repassa data: ...\n\n instantaneamente).
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

        # Rotas de telemetria do dashboard Hermes (proxy autenticado).
        # A app chama /dashboard/<rota>; expoem-se só as read-only úteis.
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
                    try:
                        import subprocess
                        subprocess.run(
                            ["hermes", "model", model],
                            check=True,
                            stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL,
                            timeout=5,
                        )
                    except Exception:
                        pass
                _send_json(self, {"success": True, "model": model})
            except Exception as e:  # noqa: BLE001
                _send_json(self, {"success": False, "error": str(e)}, status=400)
            return
        if self.path.rstrip("/") == "/profile/select":
            try:
                data = json.loads((body or b"{}").decode())
                prof = data.get("profile", "default")
                # Tenta mudar o perfil activo via CLI do Hermes.
                os.environ["HERMES_PROFILE"] = prof
                try:
                    import subprocess
                    subprocess.run(
                        ["hermes", "profile", "use", prof],
                        check=True,
                        stdout=subprocess.DEVNULL,
                        stderr=subprocess.DEVNULL,
                        timeout=5,
                    )
                except Exception:
                    pass
                _send_json(self, {"success": True, "profile": prof})
            except Exception as e:  # noqa: BLE001
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
