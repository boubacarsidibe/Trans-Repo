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
