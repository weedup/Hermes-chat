# Hermes-chat

App Android para falar com o **Hermes Agent** a correr no telemóvel (Termux + PRoot).

## Como liga

- **Porta 9119** = *dashboard* do Hermes (painel web com token próprio). **Não serve para a app** — responde 401/405.
- **Porta 8642** = gateway API do Hermes (OpenAI-compatível). Exige a chave `API_SERVER_KEY` (em `~/.hermes/.env`), mas a app **não envia autenticação**.
- **Porta 9120** = a ponte desta repo: aceita pedidos sem auth e encaminha para o 8642 injetando a chave.

## Setup no Termux / PRoot

```bash
python3 scripts/hermes_chat_bridge.py     # escuta em 127.0.0.1:9120
```

Para arrancar automaticamente, adiciona ao teu `.termux/boot/arranque_hermes.sh`:

```bash
nohup python3 <caminho-do-repo>/scripts/hermes_chat_bridge.py >> ~/hermes_chat_bridge.log 2>&1 &
```

## Na app

- URL do servidor (Definições): `http://127.0.0.1:9120` (já é o default desde esta versão)
- Endpoint: `AUTO`
- Modelo: `hermes-agent` (default; o gateway aceita qualquer nome)

## Notas

- O Hermes corre o agente completo por cada mensagem: respostas de 30–60 s são normais.
  Os timeouts da app foram subidos para 5 minutos — demora, mas não desiste a meio.
- Build do APK: GitHub Actions (`.github/workflows/blank.yml`) gera o debug APK em artefactos.

## Notificações (crons → app)

A ponte mantém uma fila de notificações; a app faz polling no mesmo ciclo do sync do modelo (10 s) e mostra-as como notificações locais (Android 13+: permissão pedida no arranque).

```bash
# enfileirar uma notificação (qualquer cron/script)
curl -s -X POST http://127.0.0.1:9120/notify \
  -H 'Content-Type: application/json' \
  -d '{"title":"Guitarra","body":"Hora de praticar — BWV 1008","tag":"guitar"}'

# o que a app faz a cada 10 s (leitura destrutiva)
curl -s http://127.0.0.1:9120/notify/pending
```

- Notificações têm TTL de 24 h na fila (não acumulam liço se a app estiver fechada dias).
- `tag` agrupa/substitui no sistema (usa `guitar`, `news`, …).
- Se a app estiver fechada, as notificações aparecem da próxima vez que abrires (drain no polling).
