# Collecteur réseau (SNMP + ICMP)

Processus centralisé unique qui sonde à distance tous les équipements
réseau déclarés dans `equipments.json` (routeurs, switches, points
d'accès) et pousse les résultats au backend toutes les
`INTERVAL_SECONDES` (60 s par défaut).

En plus de ce polling périodique, un récepteur de traps SNMP (v1/v2c)
tourne en tâche de fond : à la réception d'un trap (`linkDown`,
`linkUp`, `coldStart`, ou tout autre) depuis un équipement déclaré dans
`equipments.json`, cet équipement est sondé immédiatement — via le même
mécanisme que le cycle normal (ping + SNMP, puis push HTTP) — au lieu
d'attendre le prochain cycle. Un trap reçu d'une IP non déclarée est
ignoré. Le récepteur tourne dans un thread séparé du polling : une
erreur à son démarrage (port déjà utilisé, port privilégié sans droits
suffisants) est journalisée et n'interrompt jamais le polling.

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
| `INTERVAL_SECONDES` | Fréquence de collecte (polling) |
| `SNMP_TRAP_ENABLED` | Active/désactive le récepteur de traps (`true` par défaut) |
| `SNMP_TRAP_BIND_ADDRESS` | Adresse d'écoute du récepteur (`0.0.0.0` par défaut) |
| `SNMP_TRAP_PORT` | Port d'écoute des traps (`1162` par défaut, voir note ci-dessous) |
| `SNMP_TRAP_COMMUNITY` | Communauté SNMP (v1/v2c) acceptée pour les traps entrants |

**Port des traps et privilèges** — le port SNMP trap standard (162) est
un port privilégié : sur Linux, seul `root` (ou un processus disposant
de la capacité `cap_net_bind_service`) peut s'y lier. Deux options :

- **Dev/test** : garder le défaut `SNMP_TRAP_PORT=1162` (port non
  privilégié) — nécessite alors de configurer les équipements pour
  envoyer leurs traps vers ce port non standard, ou de rediriger le
  trafic UDP/162 vers 1162 (ex. `iptables`/`nftables` en local).
- **Prod (`SNMP_TRAP_PORT=162`)**, via systemd, sans lancer l'agent en
  `root` :
  ```ini
  # /etc/systemd/system/agent-network.service (extrait, cf. section suivante)
  [Service]
  AmbientCapabilities=CAP_NET_BIND_SERVICE
  ```
  systemd accorde alors uniquement la capacité de se lier aux ports
  privilégiés, sans exécuter le processus en tant que `root`.

`equipments.json` : un objet par équipement réseau à superviser —
`equipment_id`/`api_key` créés côté backend, `ip_address`,
`snmp_community`, `snmp_port`, `interface_index` (index SNMP de
l'interface à surveiller, cf. `ifTable`).

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
# Necessaire uniquement si SNMP_TRAP_PORT=162 (port privilegie) sans lancer
# le service en root : accorde juste la capacite de s'y lier.
AmbientCapabilities=CAP_NET_BIND_SERVICE

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now agent-network
```
