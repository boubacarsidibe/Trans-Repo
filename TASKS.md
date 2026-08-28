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

## Intégration

- [ ] Test d'intégration bout-en-bout sans interface : un agent Python
      (`agent/system` ou `agent/network`) envoie des métriques à une instance
      backend réelle, déclenche un dépassement de seuil, et on vérifie qu'une
      alerte est créée et diffusée sur le canal WebSocket de supervision.
  - Critères : script ou test reproductible documenté (comment le lancer
    localement), passe sans intervention manuelle une fois le backend
    démarré.

## Documentation

- [ ] README à la racine du dépôt : vue d'ensemble de l'architecture
      (backend Spring Boot / frontend React / agents Python), schéma simple
      des flux (agent → API → base → WebSocket/rapport), instructions de
      lancement local de l'ensemble, principales variables d'environnement.
  - Critères : un nouvel arrivant peut lancer backend + frontend + un agent
    en suivant uniquement ce README.

- [ ] Documentation des agents (`agent/system/README.md`,
      `agent/network/README.md`) : installation, configuration (`.env`),
      lancement, et une méthode simple pour les faire tourner en continu
      (service systemd ou tâche planifiée) sur un poste Linux du CRI.
  - Critères : README par agent avec exemple de fichier `.env` complété et
    la commande/unit systemd d'exemple.

## Déploiement

- [ ] Conteneurisation Docker : `Dockerfile` backend, `Dockerfile` frontend,
      `docker-compose.yml` de développement (backend + frontend + Postgres),
      cohérent avec la mention « déploiement conteneurisé (specs 12.3) »
      déjà présente dans `application.properties`.
  - Critères : `docker compose up` démarre les trois services et le
    frontend peut se connecter au backend sans configuration
    supplémentaire.

## Sécurité

- [ ] Revue de sécurité de l'API et du frontend (OWASP Top 10 : injection,
      auth, contrôle d'accès, exposition de données sensibles, CORS,
      en-têtes de sécurité) et correction des failles trouvées.
  - Critères : liste des points vérifiés consignée dans le commit ou dans
    `DECISIONS.md` si un correctif implique un choix, aucune régression sur
    `bash mvnw test` / `npm run build`.
