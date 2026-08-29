# Agent système

Collecte les métriques d'un serveur (CPU, RAM, disque, et quelques sondes
optionnelles) et les envoie au backend toutes les `INTERVAL_SECONDES`
(60 s par défaut). Un agent = un équipement déclaré côté backend.

## Installation

```bash
cd agent/system
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

## Configuration (`.env`)

| Variable | Rôle |
|---|---|
| `BACKEND_URL` | URL du backend (ex. `http://localhost:8080`) |
| `EQUIPMENT_ID` | UUID de l'équipement, créé au préalable via l'API/le frontend (`Équipements`) |
| `API_KEY` | Clé API propre à cet équipement (générée à sa création) |
| `INTERVAL_SECONDS` | Fréquence de collecte |
| `SEND_MAX_RETRIES` / `SEND_RETRY_BACKOFF_SECONDS` | Tentatives d'envoi en cas d'erreur réseau, avant d'abandonner jusqu'au cycle suivant |

Les sondes optionnelles (TCP, DNS, log, fichier surveillé, Modbus) restent
désactivées tant que leurs variables associées ne sont pas renseignées —
voir les commentaires dans `.env.example`.

## Lancement

```bash
python system_agent.py
```

## Tourner en continu (systemd)

```ini
# /etc/systemd/system/agent-system.service
[Unit]
Description=Agent de supervision systeme EPT
After=network.target

[Service]
WorkingDirectory=/opt/trans-repo/agent/system
ExecStart=/opt/trans-repo/agent/system/.venv/bin/python system_agent.py
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now agent-system
```
