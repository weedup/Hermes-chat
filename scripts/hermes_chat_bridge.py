#!/usr/bin/env python3
"""Ponte Hermes-chat: 127.0.0.1:9120 → 127.0.0.1:8642 (gateway OpenAI-compat).
Injeta API_SERVER_KEY do .env do Hermes. Serve /profile (nome do agente via
SOUL.md + custom_providers) e /profiles + /profile/select (perfis Hermes).
Retransmite respostas streaming (SSE) chunk-a-chunk."""
import http.client
import json
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERMES_HOME = "/root/.hermes"
GATEWAY_HOST = "127.0.0.1"
GATEWAY_PORT = 8642
LISTEN = ("127.0.0.1", 9120)


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
        is_stream = "text/event-stream" in ctype or "stream" in ctype or (body and b'"stream": true' in body)

        self.send_response(resp.status)
        for key, val in resp.getheaders():
            if key.lower() in ("transfer-encoding", "connection", "keep-alive", "content-length"):
                continue
            self.send_header(key, val)

        if is_stream:
            # Streaming: retransmite os chunks à medida que chegam do gateway.
            self.send_header("Connection", "close")
            self.end_headers()
            try:
                while True:
                    chunk = resp.read(1024)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
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
        self._relay()

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else None
        if self.path.rstrip("/") == "/profile/select":
            try:
                data = json.loads((body or b"{}").decode())
                prof = data.get("profile", "default")
                os.environ["HERMES_PROFILE"] = prof
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
