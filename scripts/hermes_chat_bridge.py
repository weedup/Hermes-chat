#!/usr/bin/env python3
import http.server
import http.client
import os
import json

UPSTREAM_HOST = "127.0.0.1"
UPSTREAM_PORT = 8642
LISTEN_PORT = 9120

ENV_CANDIDATES = [
    os.path.expanduser("~/.hermes/.env"),
    "/root/.hermes/.env",
    "/data/data/com.termux/files/home/.hermes/.env",
]

def load_key():
    for path in ENV_CANDIDATES:
        try:
            with open(path) as fh:
                for line in fh:
                    if line.startswith("API_SERVER_KEY="):
                        return line.split("=", 1)[1].strip()
        except OSError:
            continue
    return ""

KEY = load_key()

def get_profile_name(folder, default_name):
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

def list_all_profiles():
    home_base = os.path.expanduser("~/.hermes")
    profiles_dir = os.path.join(home_base, "profiles")
    active_profile = os.environ.get("HERMES_PROFILE") or "default"
    
    results = []
    results.append({
        "id": "default",
        "name": get_profile_name(home_base, "Agent T"),
        "active": (active_profile == "default")
    })
    
    if os.path.isdir(profiles_dir):
        for d in sorted(os.listdir(profiles_dir)):
            p = os.path.join(profiles_dir, d)
            if os.path.isdir(p):
                results.append({
                    "id": d,
                    "name": get_profile_name(p, d.capitalize()),
                    "active": (active_profile == d)
                })
    return {"current": active_profile, "profiles": results}

class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _forward(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else None
        headers = {
            k: v
            for k, v in self.headers.items()
            if k.lower() not in ("host", "content-length", "authorization", "connection")
        }
        if KEY:
            headers["Authorization"] = "Bearer %s" % KEY
        try:
            conn = http.client.HTTPConnection(UPSTREAM_HOST, UPSTREAM_PORT, timeout=300)
            conn.request(self.command, self.path, body=body, headers=headers)
            resp = conn.getresponse()
            data = resp.read()
            conn.close()
        except Exception as exc:
            payload = ('{"error":"upstream: %s"}' % exc).encode()
            self.send_response(502)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        self.send_response(resp.status)
        for key, val in resp.getheaders():
            if key.lower() in ("transfer-encoding", "connection", "keep-alive", "content-length"):
                continue
            self.send_header(key, val)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path in ("/", ""):
            payload = b'{"status":"ok","proxy":"hermes-chat-bridge"}'
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        if self.path.rstrip("/") in ("/profile", "/profiles", "/api/profiles"):
            info = list_all_profiles()
            payload = json.dumps(info, ensure_ascii=False).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        self._forward()

    def do_POST(self):
        if self.path.rstrip("/") == "/profile/select":
            length = int(self.headers.get("Content-Length") or 0)
            body = self.rfile.read(length) if length else b"{}"
            try:
                data = json.loads(body.decode())
                prof = data.get("profile", "default")
                os.environ["HERMES_PROFILE"] = prof
                payload = json.dumps({"success": True, "profile": prof}).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
                return
            except Exception as e:
                payload = json.dumps({"success": False, "error": str(e)}).encode()
                self.send_response(400)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
                return
        self._forward()

    do_PUT = _forward
    do_DELETE = _forward

    def log_message(self, *args):
        pass

if __name__ == "__main__":
    server = http.server.ThreadingHTTPServer(("127.0.0.1", LISTEN_PORT), Handler)
    print("hermes-chat bridge em 127.0.0.1:%d -> %d (auth: %s)"
          % (LISTEN_PORT, UPSTREAM_PORT, "OK" if KEY else "SEM CHAVE!"))
    server.serve_forever()
