# Collecteur réseau (SNMP + ICMP)

Processus qui sonde à distance tous les équipements réseau déclarés dans
`equipments.json` (routeurs, switches, points d'accès) et pousse les
résultats au backend toutes les `INTERVAL_SECONDES` (60 s par défaut).

Historiquement centralisé en une seule instance — point de panne unique
pour toute la supervision réseau (issue #157). Il peut désormais tourner en
redondance simple, deux instances dont une seule active à la fois : voir
[Redondance (deux instances)](#redondance-deux-instances) plus bas.

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

Variables de redondance (facultatives, cf. section suivante) :

| Variable | Rôle |
|---|---|
| `COLLECTOR_ID` | Identifiant de cette instance (ex. `collecteur-primaire`) |
| `COLLECTOR_ROLE` | `unique` (défaut, comportement historique), `primaire` ou `secondaire` |
| `COLLECTOR_API_KEY` | Clé partagée pour le heartbeat backend (`app.collecteurs.cle-api`) |
| `HEARTBEAT_PORT` | Port du heartbeat HTTP local de l'instance active (8091 par défaut) |
| `PEER_HEARTBEAT_URL` | URL du heartbeat de la primaire (requis si `COLLECTOR_ROLE=secondaire`) |
| `FAILOVER_CYCLES_TOLERES` | Cycles sans heartbeat de la primaire avant bascule (3 par défaut) |
| `PEER_CHECK_TIMEOUT_SECONDS` | Timeout de l'appel au heartbeat de la primaire (3 s par défaut) |

`equipments.json` : un objet par équipement réseau à superviser —
`equipment_id`/`api_key` créés côté backend, `ip_address`,
`snmp_community`, `snmp_port`, `interface_index` (index SNMP de
l'interface à surveiller, cf. `ifTable`).

## Lancement

```bash
python network_collector.py
```

## Redondance (deux instances)

Deux instances du collecteur peuvent tourner en parallèle, sur des machines
distinctes — une seule est active (elle sonde les équipements et pousse les
métriques) à la fois :

- **Primaire** (`COLLECTOR_ROLE=primaire`) : active dès le démarrage, comme
  l'instance unique historique. Expose en plus un heartbeat HTTP local
  (`GET http://<host-primaire>:HEARTBEAT_PORT/heartbeat`).
- **Secondaire** (`COLLECTOR_ROLE=secondaire`) : démarre en veille (ne sonde
  aucun équipement) et interroge le heartbeat de la primaire
  (`PEER_HEARTBEAT_URL`) à chaque cycle. Après `FAILOVER_CYCLES_TOLERES`
  cycles consécutifs sans réponse, elle prend le relais : elle devient active
  (sonde les équipements, expose son propre heartbeat) et le reste — pas de
  bascule automatique en sens inverse si la primaire revient, pour éviter les
  allers-retours ; un retour à la normale se fait en relançant la secondaire
  après avoir résolu l'incident sur la primaire.

Exemple `.env` primaire :

```
COLLECTOR_ID=collecteur-primaire
COLLECTOR_ROLE=primaire
COLLECTOR_API_KEY=<cle-partagee>
HEARTBEAT_PORT=8091
```

Exemple `.env` secondaire :

```
COLLECTOR_ID=collecteur-secondaire
COLLECTOR_ROLE=secondaire
COLLECTOR_API_KEY=<cle-partagee>
PEER_HEARTBEAT_URL=http://<host-primaire>:8091/heartbeat
FAILOVER_CYCLES_TOLERES=3
```

Quand `COLLECTOR_ID`/`COLLECTOR_API_KEY` sont renseignés, l'instance active
pousse à chaque cycle un heartbeat au backend
(`POST /api/v1/collectors/heartbeat`, en-tête `X-Collector-Key`). Côté
backend, `app.collecteurs.cle-api` (variable d'environnement
`COLLECTEURS_CLE_API`) doit être configurée à la même valeur que
`COLLECTOR_API_KEY` — vide par défaut, la route reste fermée tant que la
redondance n'est pas explicitement activée. `CollecteurWatchdog` surveille
alors ce heartbeat exactement comme `DisponibiliteWatchdog` surveille le
silence des équipements (F3) : au-delà de `WATCHDOG_CYCLES_TOLERES` cycles de
`COLLECTE_INTERVALLE_SECONDES` sans heartbeat de l'instance active, un
événement `collector_status_changed` est diffusé sur `/ws/status`.

En usage normal (une seule instance, pas de redondance), laisser
`COLLECTOR_ROLE=unique` (défaut) : le comportement est identique à avant
cette fonctionnalité, avec ou sans `COLLECTOR_ID`/`COLLECTOR_API_KEY`
configurés pour tout de même bénéficier du heartbeat backend.

Le récepteur de traps SNMP (`SNMP_TRAP_ENABLED`, voir plus haut) ne tourne
que sur l'instance active : la primaire (ou l'instance `unique`) l'ouvre dès
le démarrage, une secondaire en veille ne l'ouvre qu'au moment où elle prend
le relais. Une secondaire en veille ne sonde aucun équipement et ne doit donc
pas non plus réagir aux traps qui leur sont associés.

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
