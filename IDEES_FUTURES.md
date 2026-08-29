# Idées futures

Idées et évolutions hors périmètre de la version actuelle. Rien ici ne
bloque le développement en cours — c'est le fourre-tout mentionné par le
planning de la semaine intensive (§1 : « toute idée bonus va dans un
fichier `IDEES_FUTURES.md`, pas dans le code »). `TASKS.md` reste la liste
de travail court terme (tests, sécurité) ; ce fichier est pour plus tard.

## Évolutions déjà identifiées au cahier des charges (F9+)

- Application mobile.
- Notifications Telegram.
- Notifications SMS.

## Rapports (F8)

- Planification automatique des rapports (journalier/hebdomadaire/mensuel),
  au-delà de la génération à la demande actuelle — `GenerationNocturne`
  couvre déjà le cas journalier automatique, pas les autres périodicités.

## Équipements (F2)

- Recherche par nom/IP/type côté serveur (`GET /api/v1/equipments`) plutôt
  qu'un filtrage entièrement côté frontend — utile si le parc d'équipements
  grossit au point que charger la liste complète devient coûteux.

## Réseau / SNMP (F4)

- Couverture MIB plus large (au-delà de sysUpTime, ifOperStatus et des
  compteurs de trafic/erreurs de l'interface) : température des
  équipements, charge CPU des routeurs/switches, etc. selon les MIB
  propriétaires des constructeurs.
- Tester le collecteur contre l'ensemble des équipements réels du CRI, pas
  seulement un échantillon.

## Qualité / robustesse

- Suite de tests automatisés frontend (Vitest + Testing Library) — voir
  `TASKS.md`.
- Suite de tests automatisés pour les agents Python (pytest) — aucune à ce
  jour, la CI ne fait qu'un `compileall`.
- Migrations Flyway versionnées à la place du `ddl-auto=update` actuel de
  Hibernate — nécessite d'abord de trancher le mécanisme de baseline (cf.
  la question posée sur l'issue #56, migration TimescaleDB).
- Tests de charge outillés (JMeter/k6) avec rapport chiffré, au-delà de la
  vérification empirique actuelle du temps de réponse.
- Audit de sécurité formel (OWASP) et tests de pénétration.

## Documentation

- Guide utilisateur par rôle (Administrateur/Technicien/Observateur) avec
  captures d'écran.
- Documentation de maintenance long terme (au-delà du README technique
  condensé actuel).
- Diagrammes de cas d'utilisation/séquence UML formalisés (le planning ne
  demandait qu'un schéma d'architecture + modèle de données minimal).

## Points en attente d'arbitrage

Suivis dans `AUDIT.md` (« Points à trancher ») plutôt que dupliqués ici,
par exemple le sort de la route `/netvision-preview`.
