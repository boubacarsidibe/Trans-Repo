# backend_trans

API Spring Boot de la console de supervision (REST + WebSocket), persistance
PostgreSQL. Reçoit les métriques des agents, évalue les seuils, gère les
alertes, la disponibilité, les rapports et le journal d'audit.

## Lancer en local

PostgreSQL requis (base `Transdb` par défaut, voir variables ci-dessous).

```bash
bash mvnw spring-boot:run     # http://localhost:8080
```

## Tests

```bash
bash mvnw verify
```

## Structure des packages

| Package | Rôle |
|---|---|
| `auth` | Authentification JWT (login, refresh, verrouillage après échecs, reset de mot de passe) |
| `equipement` | Déclaration et gestion des équipements supervisés, clé API par équipement |
| `metrique` | Réception des métriques (système/réseau), agrégation horaire et journalière |
| `seuil` | Seuils d'alerte par équipement et type de métrique, seuils par défaut |
| `alerte` | Évaluation des seuils, création d'alertes, anti-répétition et élévation de sévérité |
| `disponibilite` | Watchdog de disponibilité des équipements (F3/F4) |
| `notification` | Notifications e-mail et rappels des alertes critiques non prises en charge |
| `rapport` | Génération de rapports (PDF), synthèse, génération nocturne planifiée |
| `audit` | Journal d'audit des actions de mutation |
| `websocket` | Diffusion temps réel (`/ws/metrics`, `/ws/alerts`, `/ws/status`), poignée de main authentifiée par JWT |
| `sante` | Point de contrôle `/api/v1/health` |
| `exception` | Gestion globale des exceptions, format des réponses d'erreur |
| `config` | Configuration Spring (sécurité, planification) |

## Variables d'environnement principales

Toutes ont une valeur par défaut adaptée au développement local (voir
`src/main/resources/application.properties`).

| Variable | Défaut | Rôle |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/Transdb` | Connexion PostgreSQL |
| `DB_USERNAME` / `DB_PASSWORD` | `admin` / `secretpassword` | Identifiants base |
| `JWT_SECRET` | (clé de dev fournie) | Signature des jetons — à changer en production |
| `JWT_EXPIRATION_MS` | `900000` (15 min) | Durée de vie du jeton d'accès |
| `JWT_REFRESH_EXPIRATION_MS` | `604800000` (7 j) | Durée de vie du jeton de rafraîchissement |
| `LOGIN_MAX_ATTEMPTS` / `LOGIN_LOCKOUT_MINUTES` | `5` / `15` | Verrouillage de compte après échecs successifs |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Origines autorisées (API REST + WebSocket) |
| `COLLECTE_INTERVALLE_SECONDES` | `60` | Fréquence de collecte de référence des agents |
| `WATCHDOG_CYCLES_TOLERES` / `WATCHDOG_PERIODE_MS` | `3` / `30000` | Seuil de déclaration d'indisponibilité |
| `NOTIFICATIONS_EMAIL_ACTIF` | `false` | Active l'envoi d'e-mails (nécessite `SMTP_HOST` etc.) |
| `NOTIFICATIONS_RAPPEL_MINUTES` | `60` | Intervalle de rappel des alertes critiques non traitées |
