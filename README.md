# Console de supervision — EPT / CRI

Projet de fin d'études : plateforme de supervision du parc réseau et des serveurs
du Centre de Ressources Informatiques (CRI) de l'École Polytechnique de Thiès.
Elle collecte les métriques des équipements (serveurs, routeurs, switches),
évalue des seuils d'alerte, tient un historique de disponibilité et diffuse
l'état du parc en temps réel à une console web.

## Architecture

```
                    ┌──────────────────┐
   agents Python ──▶│  backend_trans   │◀── frontend (React)
   (clé API par     │  Spring Boot     │    console de supervision
    équipement)     │  + PostgreSQL    │    (JWT, WebSocket)
                    └──────────────────┘
```

Trois sous-systèmes, déployés indépendamment :

- **`backend_trans/`** — API Spring Boot (REST + WebSocket) et persistance
  PostgreSQL. Reçoit les métriques des agents (authentifiés par clé API par
  équipement), évalue les seuils, gère les alertes, la disponibilité, les
  rapports et le journal d'audit. Diffuse les mises à jour temps réel sur
  `/ws/metrics`, `/ws/alerts`, `/ws/status` (poignée de main authentifiée par
  JWT). Voir [`backend_trans/README.md`](backend_trans/README.md).
- **`frontend/`** — Console web React. Authentification par compte (JWT),
  accès différencié par rôle (observateur / technicien / administrateur).
  Voir [`frontend/README.md`](frontend/README.md).
- **`agent/`** — Agents Python déployés sur les équipements supervisés.
  `agent/system/` collecte les métriques d'une machine locale (CPU, mémoire,
  disque, etc. via `psutil`) ; `agent/network/` interroge les équipements
  réseau distants (ping, SNMP). Les deux poussent leurs relevés vers l'API par
  cycle, avec une clé API propre à chaque équipement.

## Lancer en local

Trois briques à démarrer séparément.

### 1. Base de données

PostgreSQL, base `Transdb` (voir `backend_trans/src/main/resources/application.properties`
pour les identifiants par défaut, surchargeables par variables d'environnement).

### 2. Backend

```bash
cd backend_trans
./mvnw spring-boot:run       # http://localhost:8080
```

### 3. Frontend

```bash
cd frontend
npm install
npm run dev                  # http://localhost:5173
```

Peut tourner sans backend ni base de données avec un jeu de données de
démonstration : voir la section "Sans backend" de `frontend/README.md`.

### 4. Agents (optionnel, nécessite un équipement déclaré côté backend)

```bash
cd agent/system    # ou agent/network
pip install -r requirements.txt
cp .env.example .env   # renseigner EQUIPMENT_ID et API_KEY de l'équipement
python system_agent.py # ou network_collector.py
```

## Suivi

Le backlog restant est suivi dans les issues GitHub du dépôt (milestones
Sprint 1 à Sprint 5). [`AUDIT.md`](AUDIT.md) trace les décisions techniques
prises en cours de route et les points encore en attente d'arbitrage.
