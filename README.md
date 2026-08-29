# Trans-Repo — Système de Monitoring des Infrastructures EPT

Plateforme de supervision des serveurs et équipements réseau de l'École
Polytechnique de Thiès : collecte de métriques, alertes sur seuils,
rapports, tout en temps réel.

Projet de fin d'études — Khadija DIENG & Boubacar SIDIBE.

## Architecture

```
agent/system   (Python)  --\
agent/network  (Python)  ---+--> HTTP  --> backend_trans (Spring Boot)  <--> PostgreSQL
                                              |
                                              +--> WebSocket natif (/ws/metrics, /ws/alerts, /ws/status) --> frontend (React)
```

- **`backend_trans/`** — API Spring Boot (Java 17) : authentification JWT,
  CRUD équipements, ingestion des métriques, moteur d'alertes, rapports,
  notifications e-mail, journal d'audit, diffusion temps réel WebSocket.
- **`frontend/`** — Application React (Vite) : tableaux de bord,
  gestion des équipements/seuils/utilisateurs, alertes, rapports.
- **`agent/system/`** — Agent Python collectant les métriques système
  (CPU/RAM/disque) d'un serveur et les envoyant au backend.
- **`agent/network/`** — Collecteur SNMP pour les équipements réseau
  (disponibilité, état des interfaces).

## Exigences fonctionnelles couvertes (F1–F8)

| # | Exigence | Où |
|---|---|---|
| F1 | Authentification + rôles (Administrateur, Technicien, Observateur) | `backend_trans/.../auth` |
| F2 | CRUD des équipements | `backend_trans/.../equipement`, page `Equipements` |
| F3 | Disponibilité (équipement silencieux ⇒ indisponible) | `backend_trans/.../disponibilite` |
| F4 | Ingestion des métriques système et réseau | `backend_trans/.../metrique`, `agent/` |
| F5 | Diffusion temps réel (WebSocket) | `backend_trans/.../websocket` |
| F6 | Moteur d'alertes sur seuils | `backend_trans/.../seuil`, `.../alerte` |
| F7 | Notifications e-mail | `backend_trans/.../notification` |
| F8 | Rapports à la demande (PDF) | `backend_trans/.../rapport`, page `Rapports` |

F9 et au-delà (application mobile, Telegram, SMS) sont des évolutions
futures, hors périmètre de cette version.

## Modèle de données (aperçu)

`utilisateurs` · `equipements` · `metriques` · `seuils_alerte` · `alertes` ·
`rapports` · `journal_audit` · `preferences_notification` — schéma détaillé
dans les entités JPA (`backend_trans/src/main/java/.../*/entity`). Le
schéma est créé/mis à jour automatiquement par Hibernate
(`spring.jpa.hibernate.ddl-auto=update`), pas de migrations Flyway
versionnées pour l'instant.

## Endpoints principaux

| Domaine | Base path |
|---|---|
| Authentification | `POST /api/auth/login`, `/api/auth/refresh` |
| Utilisateurs | `/api/v1/users` |
| Équipements | `/api/v1/equipments` |
| Métriques (ingestion agents, clé API) | `POST /api/v1/metrics/system`, `POST /api/v1/metrics/network` |
| Métriques (lecture) | `GET /api/v1/equipments/{id}/metrics` |
| Seuils | `/api/v1/thresholds` |
| Alertes | `/api/v1/alerts` |
| Rapports | `/api/v1/reports` |
| Journal d'audit | `/api/v1/audit-log` |
| Préférences de notification | `/api/v1/users/me/notifications` |
| Santé | `GET /api/v1/health` |
| Temps réel | `ws(s)://.../ws/metrics`, `/ws/alerts`, `/ws/status` (WebSocket natif, pas STOMP) |

## Lancer le projet en local

Prérequis : Docker + Docker Compose.

```bash
cp .env.example .env
docker compose up --build
```

- Frontend : http://localhost:5173
- Backend : http://localhost:8080
- PostgreSQL : localhost:5432

### Sans Docker (développement au jour le jour)

Backend :
```bash
cd backend_trans
bash mvnw spring-boot:run
```
(nécessite un PostgreSQL local — voir `application.properties` pour les
variables `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`)

Frontend :
```bash
cd frontend
npm install
npm run dev
```

Agents : voir `agent/system/README.md` et `agent/network/README.md`.

## Déploiement

Voir `deploy/README.md` — cible : serveur CRI de l'EPT, `docker-compose.prod.yml`
+ reverse proxy nginx, images publiées sur GHCR par la CI (`.github/workflows/ci-cd.yml`).

## Variables d'environnement principales

| Variable | Rôle |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Connexion PostgreSQL |
| `JWT_SECRET` | Signature des jetons d'authentification |
| `CORS_ALLOWED_ORIGINS` | Origines autorisées côté API/WebSocket |
| `VITE_API_BASE_URL` | URL du backend vue par le frontend (build) |
| `SMTP_HOST`/`SMTP_PORT`/`SMTP_USERNAME`/`SMTP_PASSWORD` | Notifications e-mail (F7), désactivées si `SMTP_HOST` est vide |

Liste complète : `backend_trans/src/main/resources/application.properties`.
