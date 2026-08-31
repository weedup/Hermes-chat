#!/usr/bin/env python3
"""Ponte Hermes-chat: aceita pedidos SEM autenticação na porta 9120 e encaminha
para o gateway API do Hermes (porta 8642) injetando a API_SERVER_KEY.

Porquê: a porta 9119 é o *dashboard* (token próprio, rotas próprias — dá 401/405).
A app Android Hermes-chat não envia header de autenticação, por isso fala com
esta ponte em http://127.0.0.1:9120.

Uso (no Termux/PRoot onde vive o Hermes):
    python3 scripts/hermes_chat_bridge.py
"""
import http.server
import http.client
import os

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


def resolve_profile_info():
    """Devolve {name, profile, aliases} do perfil Hermes ativo.

    Lê $HERMES_HOME (default ~/.hermes). O nome "amigável" do agente é tirado do
    SOUL.md (primeira linha 'You are <Nome>'), com fallback para o nome do
    perfil activo. Mapeia 'default' -> 'Agent T' se o SOUL.md não disser nada.
    """
    home = os.environ.get("HERMES_HOME") or os.path.expanduser("~/.hermes")
    profile = os.path.basename(home.rstrip("/")) or "default"
    if profile in (".hermes", "hermes"):
        profile = "default"

    # name do SOUL.md
    friendly = ""
    for soul_path in (os.path.join(home, "SOUL.md"),):
        try:
            with open(soul_path) as fh:
                for line in fh:
                    stripped = line.strip()
                    if stripped.lower().startswith("you are "):
                        friendly = stripped[len("you are "):].strip()
                        break
                    if stripped.startswith("# "):
                        friendly = stripped[2:].strip()
                        break
        except OSError:
            continue

    if not friendly:
        friendly = profile if profile != "default" else "Agent T"

    # alias a partir do config custom_providers (ex.: 'name: Agent T')
    alias = friendly
    try:
        import yaml
        with open(os.path.join(home, "config.yaml")) as fh:
            cfg = yaml.safe_load(fh)
        cps = cfg.get("custom_providers", [])
        if isinstance(cps, list) and cps:
            first = cps[0]
            if isinstance(first, dict) and first.get("name"):
                alias = str(first["name"])
    except Exception:
        pass

    return {"name": friendly, "profile": profile, "alias": alias}


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
        except Exception as exc:  # noqa: BLE001
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
        if self.path.rstrip("/") == "/profile":
            import json
            info = resolve_profile_info()
            payload = json.dumps(info, ensure_ascii=False).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        self._forward()

    do_POST = _forward
    do_PUT = _forward
    do_DELETE = _forward

    def log_message(self, *args):  # silencioso
        pass


if __name__ == "__main__":
    server = http.server.ThreadingHTTPServer(("127.0.0.1", LISTEN_PORT), Handler)
    print("hermes-chat bridge em 127.0.0.1:%d -> %d (auth: %s)"
          % (LISTEN_PORT, UPSTREAM_PORT, "OK" if KEY else "SEM CHAVE!"))
    server.serve_forever()
