# Tâches

Liste de travail pour l'agent autonome (relancé toutes les heures). Une tâche
à la fois, dans l'ordre. Cocher uniquement quand les critères de validation
sont vraiment remplis.

## Tests backend

- [ ] Tests auth & sécurité par rôle — `AuthController`, `UserController` et
      la config de sécurité (`SecurityConfig`).
  - Critères : connexion valide/invalide, verrouillage après
    `security.login.max-attempts`, rafraîchissement de token (valide/expiré),
    accès aux endpoints selon rôle (observateur / technicien / administrateur,
    401/403 attendus). `bash mvnw test` vert.

- [ ] Tests équipements — `EquipementController` + `EquipementService`.
  - Critères : CRUD complet, restrictions par rôle (déclaration réservée aux
    techniciens/admin), génération et validité de la clé d'agent. `bash mvnw
    test` vert.

- [ ] Tests seuils & alertes — `SeuilAlerteController`/`SeuilAlerteService`,
      `MetriqueSeuilEvaluator`, `AlerteController`/`AlerteService`,
      `DisponibiliteWatchdog`.
  - Critères : CRUD des seuils, valeurs par défaut (`SeuilsParDefaut`),
    déclenchement d'une alerte quand un seuil est dépassé, cycle de vie
    complet d'une alerte (déclenchée → prise en compte → résolue),
    déclenchement par le watchdog de disponibilité après le nombre de cycles
    tolérés. `bash mvnw test` vert.

- [ ] Tests métriques & rapports — `MetriqueController` (ingestion
      `SystemMetricsRequest`), `RapportController`/`RapportService`,
      `RapportCalculateur`.
  - Critères : ingestion des métriques système/réseau, calcul correct des
    valeurs agrégées d'un rapport sur une plage donnée, génération à la
    demande. `bash mvnw test` vert.

- [ ] Tests notifications & audit — `NotificationAlerteService`,
      `RappelAlertesCritiques`, `PreferenceNotificationController`,
      `JournalAuditController`/`JournalAuditService`.
  - Critères : notification envoyée/non envoyée selon
    `app.notifications.email.actif` et les préférences utilisateur, rappel
    déclenché pour une alerte critique non prise en charge après le délai
    configuré, actions sensibles bien tracées dans le journal d'audit.
    `bash mvnw test` vert.

## Tests frontend

- [ ] Mettre en place des tests frontend (Vitest + Testing Library, aligné
      avec Vite) et couvrir les écrans critiques : `LoginPage`,
      `EquipementsPage`, `AlertesPage`.
  - Critères : `npm run test` (nouveau script à ajouter dans
    `package.json`) vert en local et dans `ci-cd.yml` (job frontend),
    scénarios nominaux + erreurs (échec de connexion, liste vide, action sur
    une alerte).

## Tests agents

- [x] Mettre en place des tests pour les agents Python (pytest) et couvrir
      la logique métier des deux agents (`checks.py`, `system_agent.py`,
      `network_collector.py`).
  - Critères : `python -m pytest` (depuis `agent/`) vert en local et dans
    `ci-cd.yml` (job agent), réseau/disque/capteurs/HTTP/SNMP simulés (pas
    de matériel ni de backend requis).
  - A découvert au passage un bug réel que `compileall` ne pouvait pas
    voir : `network_collector.py` importe `pysnmp.hlapi.v3arch.asyncio`,
    module apparu seulement en pysnmp 7.1.10+, alors que
    `network/requirements.txt` épinglait `pysnmp==6.2.6` — l'agent réseau
    aurait plané au démarrage avec `ModuleNotFoundError`. Corrigé vers
    `pysnmp==7.1.29`.

## Intégration

- [ ] Test d'intégration bout-en-bout sans interface : un agent Python
      (`agent/system` ou `agent/network`) envoie des métriques à une instance
      backend réelle, déclenche un dépassement de seuil, et on vérifie qu'une
      alerte est créée et diffusée sur le canal WebSocket de supervision.
  - Critères : script ou test reproductible documenté (comment le lancer
    localement), passe sans intervention manuelle une fois le backend
    démarré.

## Documentation

- [x] README à la racine du dépôt : vue d'ensemble de l'architecture
      (backend Spring Boot / frontend React / agents Python), mapping F1-F8,
      endpoints principaux, instructions de lancement local (Docker et sans
      Docker).

- [x] Documentation des agents (`agent/system/README.md`,
      `agent/network/README.md`) : installation, configuration (`.env`),
      lancement, unit systemd d'exemple pour tourner en continu.

## Déploiement

- [x] Conteneurisation Docker : `Dockerfile` backend, `Dockerfile` frontend,
      `docker-compose.yml` de développement (backend + frontend + Postgres).
      `docker compose config` valide (build réel non vérifiable ici, pas de
      daemon Docker dans cet environnement — à confirmer en local/CRI).

- [x] `docker-compose.prod.yml` + reverse proxy nginx (`deploy/nginx.conf`,
      route `/api` et `/ws` vers le backend, sert le frontend) + job
      `deploy` (`ci-cd.yml`) publiant sur GHCR et déployant sur le serveur
      CRI par SSH. Secrets GitHub (`CRI_HOST`/`CRI_USER`/`CRI_SSH_KEY`/
      `CRI_DEPLOY_PATH`) et accès réel au serveur CRI encore à mettre en
      place — voir `deploy/README.md`.

## Sécurité

- [ ] Revue de sécurité de l'API et du frontend (OWASP Top 10 : injection,
      auth, contrôle d'accès, exposition de données sensibles, CORS,
      en-têtes de sécurité) et correction des failles trouvées.
  - Déjà en place : BCrypt, JWT, CORS restreint (`app.cors.allowed-origins`),
    verrouillage après échecs de connexion (`security.login.max-attempts`).
  - Trouvé et à trancher : `POST /api/auth/register` est public et crée des
    comptes avec un rôle `CLIENT` hors du modèle de rôles réel
    (`ADMINISTRATEUR`/`TECHNICIEN`/`OBSERVATEUR`) — reliquat d'un template
    générique. La vraie création d'utilisateurs est déjà admin-only
    (`POST /api/v1/users`). Proposition : supprimer `/register` et les
    rôles legacy (`ADMIN`/`MANAGER`/`OPERATOR`/`CLIENT`) de l'enum `Role`.

## Nettoyage

- [x] `backend_trans/agent/` retiré : copie périmée et divergente du vrai
      `agent/` à la racine (sans README ni `checks.py`).
