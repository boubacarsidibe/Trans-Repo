# Agents Python

Deux agents indépendants, chacun poussé périodiquement vers l'API Spring Boot
(`backend_trans`), authentifiés par une clé API propre à l'équipement
(en-tête `X-API-Key`).

## `system/` — agent local

Installé sur chaque serveur à superviser. Collecte à intervalle régulier
(`INTERVAL_SECONDS`, 60 s par défaut) via `psutil` : CPU, mémoire, disque,
et pousse le relevé en `POST /api/v1/metrics/system`.

```bash
cd agent/system
pip install -r requirements.txt
cp .env.example .env   # EQUIPMENT_ID et API_KEY de l'équipement, cf. backend
python system_agent.py
```

Un agent minimal ne demande que `EQUIPMENT_ID`/`API_KEY` : les sondes
supplémentaires de `checks.py` (TCP, DNS, fichier de log, fichier surveillé,
Modbus TCP) restent désactivées tant que leur(s) variable(s) d'environnement
ne sont pas renseignées dans `.env` — chacune est indépendante des autres. La
lecture Modbus n'a pas été validée sur du matériel réel (voir issue dédiée)
et est à vérifier avant toute mise en production sur un site avec des
automates.

## `network/` — collecteur réseau

Centralisé : un seul processus sonde à distance tous les équipements réseau
(routeurs, switches, points d'accès) déclarés dans `equipments.json`, puis
pousse en `POST /api/v1/metrics/network`. SNMP (v2c) donne bande passante et
taux d'erreur via les compteurs `ifTable` (MIB-II) ; ICMP donne latence et
disponibilité.

```bash
cd agent/network
pip install -r requirements.txt
cp .env.example .env
cp equipments.example.json equipments.json   # un objet par equipement
python network_collector.py
```

Chaque entrée de `equipments.json` associe un équipement à sa clé API et à
ses paramètres de sonde :

```json
{
  "nom": "Switch-Coeur-CRI",
  "equipment_id": "<uuid de l'equipement cote backend>",
  "api_key": "<cle API de l'equipement>",
  "ip_address": "10.0.0.1",
  "snmp_community": "public",
  "snmp_port": 161,
  "interface_index": 1
}
```

## Exécution durable (systemd)

En développement, `python system_agent.py`/`python network_collector.py`
suffit. Pour qu'un agent survive à un redémarrage de la machine et reparte
après un plantage, chacun a son unit systemd : `system/system-agent.service`
et `network/network-collector.service`.

Installation (à répéter pour chaque agent, en remplaçant les chemins) :

```bash
# 1. Déployer le code sur la machine (serveur supervisé pour system/, poste
#    centralisé du CRI pour network/), à l'emplacement attendu par le unit
#    (par défaut /opt/monitoring-ept/agent/<system|network> — sinon, adapter
#    WorkingDirectory/EnvironmentFile/ExecStart dans le fichier .service).
sudo mkdir -p /opt/monitoring-ept/agent/system
sudo cp -r agent/system/* /opt/monitoring-ept/agent/system/
cd /opt/monitoring-ept/agent/system

# 2. Environnement virtuel dédié (le unit pointe sur .venv/bin/python).
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt

# 3. Configuration (jamais commitée : lue directement par systemd via
#    EnvironmentFile, pas besoin de python-dotenv en production).
cp .env.example .env
# éditer .env : EQUIPMENT_ID/API_KEY (system) ou EQUIPMENTS_CONFIG_PATH +
# equipments.json (network)

# 4. Utilisateur de service dédié, sans privilèges (une seule fois pour les
#    deux agents s'ils tournent sur la même machine).
sudo useradd --system --no-create-home --shell /usr/sbin/nologin monitoring
sudo chown -R monitoring:monitoring /opt/monitoring-ept

# 5. Installer et démarrer le unit.
sudo cp system-agent.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now system-agent.service
sudo systemctl status system-agent.service
journalctl -u system-agent.service -f
```

Les deux units redémarrent automatiquement en cas d'échec
(`Restart=on-failure`) et laissent 30 s à l'agent pour terminer son cycle en
cours avant de couper (les deux scripts gèrent déjà `SIGINT`/`SIGTERM`
proprement). `network-collector.service` n'écrit rien sur disque (état entre
deux cycles gardé en mémoire) ; `system-agent.service` a besoin d'écrire
`agent_state.json` (`STATE_FILE_PATH`) dans son répertoire de travail, seul
chemin laissé inscriptible par le durcissement du unit
(`ProtectSystem=strict` + `ReadWritePaths`).
