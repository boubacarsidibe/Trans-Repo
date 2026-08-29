# Déploiement — serveur CRI (EPT) + docker-compose

Cible retenue pour l'issue #38 : le serveur CRI de l'EPT, qui exécute
`docker-compose.yml` avec des images pré-construites, publiées par la CI sur
GitHub Container Registry (GHCR) à chaque push sur `main`.

Ce répertoire (`deploy/`) est ce qui vit **sur le serveur CRI**, pas dans un
checkout du dépôt complet : le serveur n'a besoin ni de Maven, ni de Node, ni
du code source, seulement de Docker et de ce `docker-compose.yml`.

## Mise en place initiale du serveur (une seule fois)

1. Docker + Docker Compose installés sur le serveur CRI.
2. Créer un répertoire de déploiement, par exemple `/opt/trans-repo`, et y
   copier `deploy/docker-compose.yml`.
3. Copier `deploy/.env.example` en `.env` dans ce même répertoire et
   renseigner de vrais secrets (`POSTGRES_PASSWORD`, `JWT_SECRET`,
   `CORS_ALLOWED_ORIGINS` avec le vrai hôte, SMTP si utilisé).
4. Si les paquets GHCR du dépôt sont privés : `docker login ghcr.io` sur le
   serveur avec un token ayant le scope `read:packages` (ou basculer les
   paquets en public dans les paramètres GitHub, plus simple pour un usage
   intranet EPT).
5. Générer une paire de clés SSH dédiée au déploiement, ajouter la clé
   publique à `~/.ssh/authorized_keys` sur le serveur CRI.

## Secrets/variables à configurer côté GitHub (Settings → Secrets and
variables → Actions)

| Nom | Type | Description |
|---|---|---|
| `CRI_HOST` | secret | Adresse du serveur CRI |
| `CRI_USER` | secret | Utilisateur SSH de déploiement |
| `CRI_SSH_KEY` | secret | Clé privée correspondant à la clé publique ajoutée ci-dessus |
| `CRI_DEPLOY_PATH` | secret | Répertoire du serveur contenant `docker-compose.yml` et `.env` (ex. `/opt/trans-repo`) |
| `PROD_API_BASE_URL` | variable | URL publique du backend (ex. `http://cri-hostname:8080`), figée dans le bundle frontend au build |

## Ce que fait le job `deploy` (`.github/workflows/ci-cd.yml`)

1. Construit et publie les images `backend`/`frontend` sur
   `ghcr.io/<owner>/trans-repo-{backend,frontend}` (tags `latest` et le SHA
   du commit).
2. Copie `deploy/docker-compose.yml` sur le serveur CRI (le `.env`, lui,
   n'est jamais touché par la CI — il ne vit que sur le serveur).
3. Se connecte en SSH et lance `docker compose pull && docker compose up -d`
   dans `CRI_DEPLOY_PATH`.

Un rollback consiste à repointer `IMAGE_TAG` dans le `.env` du serveur vers
un SHA de commit antérieur (les deux tags sont publiés à chaque
déploiement), puis relancer `docker compose pull && up -d` à la main.

**État actuel (issue #38)** : mécanisme prêt côté CI/CD, mais aucun
déploiement réel n'a encore eu lieu — l'accès au serveur CRI (host,
utilisateur, clé SSH) reste à obtenir et les secrets GitHub ci-dessus à
configurer. En attendant, le développement reste local.
