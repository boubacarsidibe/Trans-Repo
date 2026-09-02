# Audit de sécurité formel (OWASP Top 10:2021)

Réalise l'issue [#141](https://github.com/boubacarsidibe/Trans-Repo/issues/141).

## Avertissement de périmètre

**Ceci n'est pas un test de pénétration.** Un vrai pentest suppose une
application déployée et joignable (staging ou prod), des outils dynamiques
(fuzzing HTTP, scanner comme OWASP ZAP/Burp, tentatives d'exploitation
réelles) et généralement une autorisation écrite (règles d'engagement). Cet
environnement de travail n'a accès qu'au code source, sans instance en
cours d'exécution ni base de données peuplée — aucune requête n'a donc été
envoyée à une application réelle.

Ce document est une **revue de code manuelle** contre la grille OWASP Top
10:2021, portant sur `backend_trans/` (Spring Boot), `frontend/` (React/Vite)
et `agent/` (Python), plus la configuration de déploiement
(`Dockerfile`, `docker-compose*.yml`, `deploy/nginx.conf`). Un vrai audit de
pénétration, avec environnement dédié et outillage dynamique, reste à
planifier séparément (cf. recommandations en fin de document).

Distinct de l'issue #39 (scan automatisé de dépendances en CI) : ce document
ne revient pas sur les CVE de dépendances, déjà couvertes par
`npm audit --audit-level=high` (frontend) et le job `dependency-check`
(OWASP dependency-check-maven, backend) dans `.github/workflows/ci-cd.yml`.

`mcp__github__run_secret_scanning` a été tenté sur les fichiers de
configuration sensibles (`application.properties`, `docker-compose.yml`,
`.env.example`) : le dépôt n'a pas GitHub Advanced Security activé, l'outil
n'a donc pas pu s'exécuter. La revue manuelle de ces fichiers n'a trouvé
aucun secret réel committé — uniquement des valeurs d'exemple explicitement
documentées comme telles (voir A02 et A05 ci-dessous pour les nuances).

## Sommaire exécutif

| Gravité | Nombre de constats |
|---|---|
| Critique | 1 |
| Élevée | 2 |
| Moyenne | 4 |
| Faible / à surveiller | 3 |
| Conforme (points forts confirmés) | 5 |

**Constat le plus critique** : le jeton de réinitialisation de mot de passe
est journalisé en clair au niveau `INFO`
(`AuthServiceImpl.java:137`), niveau actif par défaut en production
(`application.properties:3`). Quiconque a accès aux logs peut donc
usurper n'importe quel compte via le flux "mot de passe oublié" — un défaut
plus grave que ce que le commentaire du code laisse penser ("Token (dev
only)").

Les deux constats de gravité élevée concernent la valeur par défaut d'un
secret de signature JWT committée dans le code source
(`application.properties:14`) et l'absence de durcissement des conteneurs
Docker (exécution en root).

À l'inverse, l'implémentation du contrôle d'accès RBAC, du hachage de mot de
passe (BCrypt), du verrouillage de compte après échecs répétés et de la
protection contre l'injection SQL/JPQL est **solide et bien conçue** — voir
détail dans le tableau ci-dessous.

## Tableau OWASP Top 10:2021

### A01:2021 — Broken Access Control

**Statut : Conforme**

- Politique "deny by default" : `.anyRequest().authenticated()` couvre tout
  endpoint non explicitement listé
  (`backend_trans/src/main/java/com/bouba/backend_trans/config/SecurityConfig.java:57`).
  Seuls `/api/auth/**`, `/api/v1/health`, la doc OpenAPI et le handshake
  `/ws/**` (contrôlé ailleurs, voir A07) sont publics
  (`SecurityConfig.java:48-55`).
- RBAC appliqué avec `@PreAuthorize` sur les contrôleurs sensibles :
  `UserController.java:29` (`hasRole('ADMINISTRATEUR')` sur tout
  `/api/v1/users/**`), `EquipementController.java:28,50,57,63`
  (lecture ouverte aux trois rôles, écriture restreinte à
  ADMINISTRATEUR/TECHNICIEN).
- Pas d'IDOR détecté sur les ressources "personnelles" : les préférences de
  notification sont résolues depuis le principal authentifié
  (`authentication.getName()`), jamais depuis un identifiant fourni par le
  client
  (`backend_trans/src/main/java/com/bouba/backend_trans/notification/PreferenceNotificationController.java:33,50-53`).
- La clé API d'un équipement (`cleApi`, utilisée par les agents pour
  s'authentifier sur `/api/v1/metrics`) n'est renvoyée qu'à la création
  (`EquipementResponse.fromEntity(created, true)` — `EquipementController.java:53`)
  et jamais dans les réponses de listing/lecture/mise à jour, qui utilisent
  l'overload par défaut masquant le champ
  (`backend_trans/src/main/java/com/bouba/backend_trans/equipement/dto/EquipementResponse.java:27-29`).
  Bon exemple de conception "moindre exposition".

### A02:2021 — Cryptographic Failures

**Statut : Non conforme**

1. **[Élevée]** Valeur par défaut du secret de signature JWT committée dans
   le code source :
   `backend_trans/src/main/resources/application.properties:14`
   (`jwt.secret=${JWT_SECRET:404E63...}`). Si la variable d'environnement
   `JWT_SECRET` n'est pas positionnée (déploiement manuel du jar, tests,
   erreur de configuration), le serveur signe tous les jetons avec cette
   valeur, publique dans l'historique Git — n'importe qui peut alors
   forger un JWT valide (y compris avec le rôle ADMINISTRATEUR) sans jamais
   s'authentifier. `docker-compose.prod.yml:27` exige déjà `JWT_SECRET` sans
   valeur par défaut (bonne pratique) ; le même principe manque dans
   `application.properties`, qui est le dernier filet de sécurité si le
   déploiement conteneurisé est contourné.
   **Remédiation** : retirer la valeur par défaut de `jwt.secret`
   (`${JWT_SECRET}` sans fallback) pour que l'application refuse de démarrer
   sans secret explicite, comme c'est déjà le cas pour `POSTGRES_PASSWORD`
   en production.

2. **[Critique — recoupe A09]** Le jeton de réinitialisation de mot de passe
   est journalisé en clair à un niveau actif en production :
   `backend_trans/src/main/java/com/bouba/backend_trans/auth/service/AuthServiceImpl.java:137`
   (`log.info("Password reset requested for {}. Token (dev only): {}", ...)`),
   alors que `logging.level.root=INFO`
   (`application.properties:3`) — donc actif par défaut, y compris en prod,
   puisqu'aucun profil ne relève ce niveau à `WARN`/`ERROR`. Le commentaire
   du code ("dev only") est trompeur : rien n'empêche cette ligne
   d'apparaître dans les logs de production tant qu'aucun envoi SMTP n'est
   branché (`app.notifications.email.actif=false` par défaut —
   `application.properties:41`), ce qui est justement le cas courant tant
   que le SMTP n'est pas configuré. Toute personne ayant accès aux logs
   (exploitation, agrégateur de logs, conteneur `docker logs`) peut alors
   prendre le contrôle de n'importe quel compte en 30 minutes (durée de
   validité du jeton, `password-reset.expiration-minutes=30`).
   **Remédiation** : passer ce log en `DEBUG` (ou le supprimer entièrement)
   et le rendre conditionnel à un profil `dev` explicite
   (`@Profile("dev")` ou test sur `spring.profiles.active`), pas au niveau
   de log global.

3. **[Faible]** Mot de passe PostgreSQL par défaut `secretpassword`
   committé comme fallback
   (`application.properties:7`, `docker-compose.yml:8,28`). Limité au
   contexte dev/CI (`docker-compose.prod.yml` n'a pas de fallback — bonne
   pratique déjà appliquée côté prod), mais un fallback silencieux reste un
   risque si un environnement "dev" se retrouve exposé par erreur.
   **Remédiation** : même traitement que le point 1, aligné sur le
   comportement déjà en place en production.

BCrypt est utilisé pour le hachage des mots de passe
(`backend_trans/src/main/java/com/bouba/backend_trans/config/SecurityBeansConfig.java:11-14`),
avec le facteur de coût par défaut de Spring Security (10) — conforme aux
recommandations actuelles.

### A03:2021 — Injection

**Statut : Conforme**

- Toutes les requêtes JPQL/SQL natives utilisent des paramètres liés
  (`:limite`, etc.), jamais de concaténation de chaînes :
  `backend_trans/src/main/java/com/bouba/backend_trans/metrique/repository/MetriqueHoraireRepository.java:23-41`,
  `MetriqueJournaliereRepository.java:22-40`,
  `AlerteRepository.java:57,71`, `MetriqueRepository.java:42,54`.
- Validation Bean Validation (`@NotBlank`, `@Email`, `@Size`) sur les DTO
  d'entrée exposés publiquement (`LoginRequest`, `ForgotPasswordRequest`,
  `ResetPasswordRequest`, `UserCreateRequest` — tous dans
  `backend_trans/src/main/java/com/bouba/backend_trans/auth/dto/`), avec
  `GlobalExceptionHandler` renvoyant une 400 structurée sur échec
  (`backend_trans/src/main/java/com/bouba/backend_trans/exception/GlobalExceptionHandler.java:16-28`).
- Aucun usage de `dangerouslySetInnerHTML` côté frontend
  (`frontend/src` — recherche exhaustive, zéro résultat), donc pas
  d'injection HTML délibérée contournant l'échappement automatique de React.

### A04:2021 — Insecure Design

**Statut : Conforme**

- Verrouillage de compte après échecs répétés, avec seuil et durée
  configurables (`security.login.max-attempts`,
  `security.login.lockout-minutes` — `application.properties:20-21`),
  implémenté dans
  `AuthServiceImpl.java:161-168` (`registerFailedAttempt`) et exposé via une
  exception dédiée `AccountLockedException` mappée en HTTP 423
  (`GlobalExceptionHandler.java:42-46`).
- Message d'erreur générique ("Invalid credentials.") à la fois pour
  identifiant inconnu et mot de passe erroné
  (`AuthServiceImpl.java:75,88`), évitant l'énumération de comptes par la
  page de connexion.
- `POST /api/auth/forgot-password` répond systématiquement `202 Accepted`
  que l'e-mail existe ou non (`AuthController.java:41-44`,
  `AuthServiceImpl.java:126-139` — le `ifPresent` ne modifie pas la
  réponse), évitant l'énumération de comptes par ce flux.

### A05:2021 — Security Misconfiguration

**Statut : Non conforme**

1. **[Élevée]** Les deux `Dockerfile` du dépôt
   (`backend_trans/Dockerfile`, `frontend/Dockerfile`) n'ont aucune
   directive `USER` : les conteneurs backend (JRE) et frontend (nginx)
   tournent tous deux en `root` par défaut. Une vulnérabilité
   d'exécution de code dans l'application ou une des dépendances
   donnerait un accès root à l'intérieur du conteneur, élargissant la
   surface d'une éventuelle évasion de conteneur.
   **Remédiation** : ajouter un utilisateur non privilégié dans chaque
   image (`RUN addgroup -S app && adduser -S app -G app` puis
   `USER app` côté backend ; l'image `nginx:alpine` fournit déjà un
   utilisateur `nginx`, ajouter simplement `USER nginx` avec les
   permissions de fichiers correspondantes côté frontend).

2. **[Moyenne]** Aucun en-tête de sécurité HTTP n'est positionné par les
   deux configurations nginx du dépôt
   (`deploy/nginx.conf`, `frontend/nginx.conf`) : pas de
   `Content-Security-Policy`, `X-Content-Type-Options`,
   `X-Frame-Options`/`frame-ancestors`, `Referrer-Policy`, ni
   `Strict-Transport-Security`. Par ailleurs, `deploy/nginx.conf` n'écoute
   qu'en HTTP (`listen 80`), sans configuration TLS visible dans ce dépôt —
   à confirmer que la terminaison TLS est bien assurée par une couche
   externe (load balancer, reverse proxy amont) non versionnée ici, sinon
   le trafic (y compris les jetons JWT en en-tête `Authorization`) circule
   en clair.
   **Remédiation** : ajouter les en-têtes via `add_header` dans les deux
   blocs `server {}`, et documenter/vérifier explicitement où la
   terminaison TLS a lieu en production.

3. **[Faible]** `spring-boot-devtools` est présent en dépendance
   `runtime`/`optional` (`backend_trans/pom.xml:104-108`). Sans risque
   direct puisque le plugin Maven Spring Boot exclut normalement les
   dépendances `optional` du jar exécutable repackagé, mais à vérifier
   explicitement (`mvn dependency:tree` sur l'artefact final) pour
   confirmer qu'aucune fonctionnalité de rechargement à chaud ni
   d'endpoint devtools n'atteint l'image de production.

Aucune dépendance `spring-boot-starter-actuator` n'est présente dans
`pom.xml` : pas de risque d'exposition d'endpoints de gestion
(`/actuator/env`, `/actuator/heapdump`, etc.) — bon point par absence.

### A06:2021 — Vulnerable and Outdated Components

**Statut : Non applicable ici** (délibérément hors périmètre, couvert par
#39). Constat complémentaire : le job `dependency-check` dans
`.github/workflows/ci-cd.yml` est en `continue-on-error: true` tant que
`NVD_API_KEY` n'est pas configuré comme secret du dépôt — la CI actuelle
n'est donc pas bloquante sur les CVE de dépendances backend, seul
`npm audit --audit-level=high` (frontend,
`.github/workflows/ci-cd.yml`, job `frontend`) bloque réellement le pipeline
aujourd'hui. Recommandé de configurer `NVD_API_KEY` et de retirer
`continue-on-error` une fois fait, mais cette action relève de #39, pas de
cet audit.

### A07:2021 — Identification and Authentication Failures

**Statut : Partiellement conforme**

Points forts : JWT à courte durée de vie (15 min par défaut,
`jwt.expiration-ms=900000` — `application.properties:15`), refresh token
opaque en UUID stocké côté serveur et révoqué à usage unique
(`AuthServiceImpl.java:106-122`, `existing.setRevoked(true)` avant réémission),
BCrypt pour les mots de passe, verrouillage de compte (voir A04).

1. **[Moyenne]** Le frontend stocke le jeton d'accès et le refresh token
   dans `localStorage`
   (`frontend/src/api/client.ts:18-19`) plutôt que dans un cookie
   `httpOnly`. Toute XSS dans la SPA React donnerait un accès complet aux
   deux jetons (vol de session complet, y compris persistant via le
   refresh token). Le risque immédiat est atténué par l'absence de
   `dangerouslySetInnerHTML` détectée (voir A03) et l'échappement
   automatique de React, mais la conception retire une couche de défense
   en profondeur qu'un cookie `httpOnly`+`Secure`+`SameSite` aurait fournie.
   **Remédiation** : envisager un stockage en cookie `httpOnly` pour le
   refresh token au minimum (le jeton d'accès de courte durée en mémoire
   JS est un compromis plus acceptable), avec protection CSRF adaptée
   (`SameSite=Strict` suffit généralement pour une API JSON pure sans
   navigation cross-site).

2. **[Faible]** Le handshake WebSocket accepte le JWT en paramètre de
   requête `?token=...` en repli quand le client ne peut pas poser d'en-tête
   (`backend_trans/src/main/java/com/bouba/backend_trans/websocket/JwtHandshakeInterceptor.java:92-103`),
   compromis documenté et nécessaire (l'API WebSocket des navigateurs ne
   permet pas d'en-têtes personnalisés à l'ouverture). Effet de bord :
   `deploy/nginx.conf` proxifie `/ws` sans désactiver son journal d'accès
   nginx, donc ce jeton se retrouve en clair dans les logs du reverse
   proxy à chaque connexion.
   **Remédiation** : ajouter `access_log off;` (ou un format de log qui
   masque la query string) sur le bloc `location /ws` de
   `deploy/nginx.conf`.

### A08:2021 — Software and Data Integrity Failures

**Statut : Conforme / Non applicable**

- Pas de désérialisation d'objets non fiables détectée : Jackson est
  utilisé exclusivement avec des DTO typés, aucun "default typing"
  polymorphique activé.
- Les images Docker publiées sur GHCR
  (`.github/workflows/ci-cd.yml`, job `deploy`) ne sont pas signées
  (pas de cosign/sigstore) et ne fixent pas de digest immuable au
  déploiement (`docker-compose.prod.yml:18,35` référence `:${IMAGE_TAG:-latest}`,
  un tag mutable). Risque faible pour un projet de cette taille, mais à
  noter comme amélioration possible pour la traçabilité de la chaîne
  d'approvisionnement logicielle si le projet grandit.

### A09:2021 — Security Logging and Monitoring Failures

**Statut : Non conforme**

1. Voir A02 point 2 (jeton de réinitialisation en clair dans les logs) —
   c'est autant une faute de journalisation que de cryptographie.
2. **[Moyenne]** Un mécanisme d'audit métier existe et fonctionne bien
   (annotation `@Auditable`, journalisée pour les mutations
   d'utilisateurs, d'équipements, d'alertes et de seuils — voir
   `backend_trans/src/main/java/com/bouba/backend_trans/auth/service/UserService.java:39,56,77`
   et équivalents dans `equipement/service/EquipementService.java`,
   `alerte/service/AlerteService.java`, `seuil/service/SeuilAlerteService.java`),
   consultable via `JournalAuditController`. Mais **aucun événement
   d'authentification n'y est tracé** : ni connexion réussie, ni échec de
   connexion, ni verrouillage de compte, ni réinitialisation de mot de
   passe — `AuthServiceImpl.java` n'a aucune annotation `@Auditable`. Cela
   limite fortement la capacité à détecter une tentative de brute force ou
   une prise de compte a posteriori, malgré le fait que
   `failedLoginAttempts`/`lockedUntil` soient déjà suivis en base
   (`AppUser`).
   **Remédiation** : étendre `@Auditable` (ou un log dédié, hors niveau
   `INFO` sensible comme au point 1) aux événements
   `CONNEXION_REUSSIE`, `CONNEXION_ECHOUEE`, `COMPTE_VERROUILLE`,
   `MOT_DE_PASSE_REINITIALISE`.

### A10:2021 — Server-Side Request Forgery (SSRF)

**Statut : Non applicable**

Aucune requête sortante server-side pilotée par une entrée utilisateur
arbitraire n'a été identifiée dans `backend_trans/`. Les adresses IP/noms
d'hôtes des équipements (`EquipementRequest`) ne sont interrogées que par
les agents Python (`agent/network/network_collector.py`,
`agent/system/system_agent.py`), qui tournent hors du backend et sont
configurés par les rôles ADMINISTRATEUR/TECHNICIEN uniquement (pas par un
utilisateur final non authentifié) — c'est la fonction même du produit
(sonder des équipements réseau internes), pas une SSRF au sens OWASP.

## Constats hors grille OWASP (observations complémentaires)

- `GlobalExceptionHandler`
  (`backend_trans/src/main/java/com/bouba/backend_trans/exception/GlobalExceptionHandler.java`)
  ne définit pas de gestionnaire générique pour `Exception`/`RuntimeException` :
  une erreur non anticipée retombe sur le comportement par défaut de Spring
  Boot (`/error`), dont le contenu dépend de `server.error.*`, non
  positionné explicitement dans `application.properties`. Par défaut Spring
  Boot masque déjà message et stacktrace hors profil dev, donc risque
  faible, mais un `@ExceptionHandler(Exception.class)` explicite,
  renvoyant un message générique sans détail d'implémentation,
  sécuriserait ce point sans dépendre du comportement implicite du
  framework.
- Les fichiers `.env.example` du dépôt (racine, `deploy/`, `frontend/`,
  `agent/network/`, `agent/system/`) ne contiennent que des valeurs
  d'exemple explicitement commentées comme telles, jamais de secret réel —
  bonne pratique déjà en place, confirmée par revue manuelle ligne à ligne
  et tentative de scan automatisé (voir en-tête du document).

## Recommandations priorisées

1. **Urgent** — Passer le log du jeton de réinitialisation de mot de passe
   en `DEBUG` (ou conditionné à un profil dev explicite) :
   `AuthServiceImpl.java:137`. Correctif isolé, à faible risque de
   régression — candidat à une petite PR dédiée plutôt qu'une nouvelle
   issue.
2. **Élevé** — Retirer la valeur par défaut de `jwt.secret` dans
   `application.properties` pour forcer un échec au démarrage sans
   `JWT_SECRET` explicite, alignée sur `docker-compose.prod.yml`.
3. **Élevé** — Faire tourner les conteneurs `backend_trans` et `frontend`
   avec un utilisateur non privilégié (`USER` dans les deux `Dockerfile`).
4. **Moyen** — Ajouter les en-têtes de sécurité HTTP standards
   (CSP, X-Content-Type-Options, X-Frame-Options, Referrer-Policy, HSTS)
   dans `deploy/nginx.conf` et `frontend/nginx.conf`, et confirmer/documenter
   où la terminaison TLS a lieu en production.
5. **Moyen** — Étendre `@Auditable` aux événements d'authentification
   (connexion, échec, verrouillage, réinitialisation de mot de passe).
6. **Faible** — Envisager un stockage du refresh token en cookie
   `httpOnly` côté frontend plutôt que `localStorage`.
7. **Faible** — Désactiver le journal d'accès nginx (ou masquer la query
   string) sur `location /ws` dans `deploy/nginx.conf`, tant que le jeton
   WebSocket continue de transiter par paramètre de requête.
8. **À planifier séparément (nouvelle issue, hors périmètre de #141)** —
   Un véritable test de pénétration dynamique (outillage type OWASP ZAP/
   Burp Suite, environnement de staging dédié, règles d'engagement
   écrites) reste à mener une fois l'application déployée en continu ; ce
   document ne le remplace pas.
