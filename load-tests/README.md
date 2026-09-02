# Tests de charge (k6)

Scripts de test de charge pour le backend (`backend_trans/`), en réponse à
l'issue #140 (`IDEES_FUTURES.md` § Qualité / robustesse). Ils remplacent la
vérification empirique manuelle du temps de réponse par des scénarios
rejouables avec seuils de performance et rapport chiffré.

**État à la livraison de ces scripts : ils n'ont pas encore été exécutés
contre une vraie instance (backend + Postgres).** Ils ont été relus
attentivement et confrontés au code des contrôleurs (endpoints, DTO,
authentification) mais aucune mesure réelle n'a été produite. L'exécution
contre un environnement réellement démarré, l'ajustement éventuel des seuils
au vu des résultats, et la consignation d'un rapport chiffré restent à faire.

## Outil choisi : k6

k6 a été préféré à JMeter pour ce projet :

- **Scripts en JavaScript**, pas de XML ni d'IDE dédié à maintenir — cohérent
  avec le reste du dépôt (frontend TypeScript/React, agent en Python) et plus
  facile à relire/faire évoluer en revue de code.
- **Binaire unique, sans JVM à provisionner** en plus de celle déjà utilisée
  par le backend Spring Boot — installation plus légère sur un poste de dev
  ou une CI.
- **Rapport chiffré natif** en sortie de `k6 run` (agrégats + percentiles +
  seuils pass/fail), sans plugin ni étape de génération de rapport séparée
  comme avec JMeter (`jmeter -g` + rapport HTML).
- Modèle de VUs/itérations qui se prête bien aux deux scénarios ciblés ici
  (agents qui poussent des métriques en boucle, utilisateurs qui rafraîchissent
  un tableau de bord).

JMeter reste une option valable (surtout si une compétence JMeter existe déjà
dans l'équipe) ; le choix ci-dessus est celui retenu pour ce dépôt, pas un
jugement définitif sur l'outil.

## Scénarios couverts

| Script | Endpoints | Authentification | Rôle simulé |
|---|---|---|---|
| `scripts/ingestion-metrics.js` | `POST /api/v1/metrics/system`, `POST /api/v1/metrics/network` | Clé API (`X-API-Key`) | Agent de supervision qui pousse ses métriques périodiques |
| `scripts/lecture-synoptique-alertes.js` | `GET /api/v1/equipments`, `GET /api/v1/alerts`, `GET /api/v1/equipments/{id}/metrics` | JWT (`Authorization: Bearer`) | Utilisateur qui rafraîchit le tableau de bord synoptique |

Ce sont les deux flux identifiés dans l'issue #140 : l'ingestion (écriture,
haute fréquence côté agents) et la lecture synoptique/alertes (lecture,
côté utilisateurs humains). Les autres endpoints (auth, gestion des
équipements/utilisateurs, rapports) ne sont pas couverts ici : ils sont soit
peu fréquents, soit hors du périmètre défini par l'issue.

## Seuils de performance cibles

| Scénario | p95 | p99 | Taux d'erreur |
|---|---|---|---|
| Ingestion (écriture) | < 200 ms | < 500 ms | < 1 % |
| Lecture synoptique/alertes | < 300 ms | < 800 ms | < 1 % |

Justification :

- **Ingestion** : c'est une insertion unique en base, sur un payload léger,
  sans jointure. La marge visée (200 ms p95) reste largement sous le cycle de
  collecte réel des agents (`app.collecte.intervalle-secondes=60`, soit 60 s)
  et sous le délai de détection du watchdog
  (`app.watchdog.periode-ms=30000`, soit 30 s) : l'ingestion ne doit jamais
  devenir le facteur limitant de ces cycles, même en cas de pic (plusieurs
  agents qui collectent au même instant).
- **Lecture** : ces endpoints font de la pagination/tri sur un historique
  potentiellement conséquent (rétention de 90 jours pour les métriques
  brutes, `app.retention.metriques-brutes-jours=90`), même si la taille de
  page est plafonnée côté contrôleur. Une tolérance un peu plus large que
  pour l'écriture est donc raisonnable, tout en restant sous le seuil
  généralement admis pour qu'un rafraîchissement de tableau de bord soit
  perçu comme immédiat par l'opérateur (voir par ex. les repères d'usabilité
  de Nielsen sur le temps de réponse perçu comme instantané).
- **Taux d'erreur < 1 %** dans les deux cas : au-delà, ce n'est plus de la
  dégradation de performance mais un début d'indisponibilité fonctionnelle.

Ce sont des valeurs de départ raisonnables, pas des chiffres mesurés sur
l'infrastructure de production réelle du CRI. À ajuster une fois de vraies
mesures obtenues (voir section suivante) et, si possible, une fois le volume
réel d'agents/utilisateurs connu.

La charge appliquée (nombre de VUs) est volontairement configurable et
modérée par défaut (voir `VUS_MAX` ci-dessous) : l'objectif est de vérifier
que l'API reste dans les seuils sous une charge plausible, pas de chercher un
point de rupture extrême — un test de type « stress test » avec des paliers
plus élevés est une extension possible mais hors périmètre de cette
livraison.

## Prérequis

1. **k6** installé localement : <https://k6.io/docs/get-started/installation/>
   (binaire unique, pas de dépendance Java).
2. **Le backend démarré** avec une base Postgres accessible (`docker compose
   up` à la racine du dépôt, ou lancement manuel de `backend_trans`).
3. **Un compte utilisateur existant** (rôle OBSERVATEUR suffit) pour le
   scénario de lecture — créez-en un via `POST /api/v1/users` (nécessite
   d'être déjà authentifié en ADMINISTRATEUR) si vous n'en avez pas.
4. **Un équipement avec une clé API** pour le scénario d'ingestion — voir
   ci-dessous.

### Créer un équipement de test avec clé API

```bash
# 1. Login admin pour obtenir un jeton
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"<email-admin>","password":"<mot-de-passe-admin>"}' \
  | jq -r '.token')

# 2. Créer un équipement avec une clé API arbitraire
curl -s -X POST http://localhost:8080/api/v1/equipments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
        "nom": "equip-charge-test",
        "adresseIp": "10.0.0.99",
        "type": "SERVEUR",
        "etat": "ACTIF",
        "cleApi": "cle-de-test-k6-a-remplacer"
      }'
# -> récupérer "id" dans la réponse : c'est EQUIPMENT_ID
```

(Adapter `"type"` à une valeur valide de `TypeEquipement` si `SERVEUR`
n'existe pas dans votre déploiement — voir
`backend_trans/src/main/java/com/bouba/backend_trans/equipement/entity/TypeEquipement.java`.)

## Lancer les tests

### Ingestion de métriques

```bash
cd load-tests
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e EQUIPMENT_ID=<uuid-de-l-equipement> \
  -e API_KEY=cle-de-test-k6-a-remplacer \
  scripts/ingestion-metrics.js
```

Pour simuler plusieurs équipements distincts plutôt qu'un seul répété sur
tous les VUs, passer `AGENTS` (une clé API désassortie de son
`equipment_id` produit un 403, pas un 201 — voir
`ApiKeyAuthenticationFilter` / `verifyAgentOwnsEquipment`) :

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e AGENTS='[{"equipmentId":"<uuid-1>","apiKey":"<cle-1>"},{"equipmentId":"<uuid-2>","apiKey":"<cle-2>"}]' \
  scripts/ingestion-metrics.js
```

### Lecture synoptique + alertes

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e LOGIN_EMAIL=<email-observateur> \
  -e LOGIN_PASSWORD=<mot-de-passe> \
  scripts/lecture-synoptique-alertes.js
```

Le jeton JWT est obtenu une seule fois dans `setup()` et expire après 15 min
par défaut (`jwt.expiration-ms`) : garder la durée totale du test nettement
en dessous, ou relever `JWT_EXPIRATION_MS` côté backend pour un test plus
long.

### Variables d'environnement communes

| Variable | Défaut | Rôle |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | URL du backend |
| `VUS_MAX` | `20` | Nombre max de VUs simultanés (charge modérée par défaut) |
| `RAMP_DURATION` | `30s` | Durée de montée puis de descente en charge |
| `HOLD_DURATION` | `2m` | Durée du palier à charge maximale |

Exemple pour pousser une charge plus soutenue :

```bash
k6 run -e VUS_MAX=50 -e HOLD_DURATION=5m -e ... scripts/ingestion-metrics.js
```

## Lire le rapport chiffré produit par k6

À la fin de `k6 run`, k6 affiche un résumé dans le terminal. Points clés à
regarder :

- **`checks`** : proportion d'assertions réussies (ex. `system: 201
  Created`). Un taux < 100 % indique des réponses inattendues (401/403 côté
  auth, 400 côté validation, 5xx côté serveur) — regarder les logs backend
  en cas de checks en échec.
- **`http_req_duration`** : distribution des temps de réponse, avec les
  percentiles `p(95)` et `p(99)` — ce sont ces valeurs qui sont comparées aux
  seuils définis dans `options.thresholds` de chaque script.
- **`http_req_failed`** : taux de requêtes en échec réseau/HTTP (5xx compris
  selon la configuration k6 par défaut) — doit rester sous 1 % ici.
- **Métriques personnalisées** (`ingest_system_duration`,
  `ingest_network_duration`, `list_equipments_duration`,
  `list_alerts_duration`, `equipment_metrics_duration`) : mêmes percentiles
  mais décomposés par endpoint, pour identifier lequel dégrade la moyenne
  globale si `http_req_duration` global dépasse un seuil.
- **Ligne `✓`/`✗` en fin de sortie pour chaque seuil** : k6 termine avec un
  code de sortie non nul si un seuil (`thresholds`) n'est pas respecté — utile
  pour un usage scripté/CI ultérieur (non mis en place ici, hors périmètre de
  cette issue).

Pour un export exploitable au-delà du résumé terminal (archivage, comparaison
entre exécutions) :

```bash
k6 run --summary-export=rapport-ingestion.json scripts/ingestion-metrics.js
```

`rapport-ingestion.json` contient alors l'ensemble des agrégats (min, max,
moyenne, percentiles, compteurs) au format JSON, exploitable pour un suivi
dans le temps.

## Limites connues / suite possible

- Pas d'intégration dans `ci-cd.yml` : ces scripts sont pensés pour une
  exécution manuelle contre un environnement local ou de démonstration, pas
  (encore) pour un déclenchement automatique en CI.
- Le scénario d'ingestion ne teste l'isolation entre équipements que si
  `AGENTS` liste plusieurs équipements distincts ; avec `EQUIPMENT_ID`/
  `API_KEY` seuls, tous les VUs réutilisent le même équipement.
- Les seuils ci-dessus n'ont pas encore été confrontés à une exécution
  réelle : à revoir dès qu'un premier rapport chiffré est disponible.
