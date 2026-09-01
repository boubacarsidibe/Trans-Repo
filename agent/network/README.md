# Collecteur réseau (SNMP + ICMP)

Processus centralisé unique qui sonde à distance tous les équipements
réseau déclarés dans `equipments.json` (routeurs, switches, points
d'accès) et pousse les résultats au backend toutes les
`INTERVAL_SECONDES` (60 s par défaut).

## Installation

```bash
cd agent/network
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
cp equipments.example.json equipments.json
```

## Configuration

`.env` :

| Variable | Rôle |
|---|---|
| `BACKEND_URL` | URL du backend |
| `EQUIPMENTS_CONFIG_PATH` | Chemin vers `equipments.json` |
| `INTERVAL_SECONDES` | Fréquence de collecte |

`equipments.json` : une liste des clés API des équipements réseau à
superviser (une par routeur/switch/point d'accès déclaré dans
l'interface). C'est tout ce qu'il y a à transporter jusqu'ici — nom,
adresse IP, communauté/port/index SNMP sont lus automatiquement à
chaque cycle depuis la fiche de l'équipement (`GET /api/v1/agents/self`),
la même que celle affichée dans l'interface. Modifier ces champs dans
l'interface suffit, rien à recopier ici.

## Lancement

```bash
python network_collector.py
```

## Tourner en continu (systemd)

```ini
# /etc/systemd/system/agent-network.service
[Unit]
Description=Collecteur reseau SNMP EPT
After=network.target

[Service]
WorkingDirectory=/opt/trans-repo/agent/network
ExecStart=/opt/trans-repo/agent/network/.venv/bin/python network_collector.py
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now agent-network
```
