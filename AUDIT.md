# Journal de suivi

Historique des décisions techniques prises pendant le développement et des points qui restent à trancher, en complément des issues GitHub. Sert de trace pour le suivi de projet.

## Décisions actées

**29/08 — Rôles legacy (#8)**
`Role.CLIENT/ADMIN/MANAGER/OPERATOR` retirés, seuls `ADMINISTRATEUR/TECHNICIEN/OBSERVATEUR` subsistent (cohérent avec la matrice RBAC §4.4). `/api/auth/register` conservé mais restreint aux administrateurs plutôt que supprimé, pour ne pas casser l'API existante — la création de comptes reste aussi possible via `POST /api/v1/users`. `UserType` conservé : utilisé par plusieurs DTO/services, ce n'est pas du code mort. Fusionné dans `develop` (PR #53).

**29/08 — Duplicata `agent/` (#9)**
`agent/` (racine) confirmé comme version de référence (dernier commit plus récent, c'est celle que la CI compile). `backend_trans/agent/`, obsolète et non référencé ailleurs dans le dépôt, supprimé.

**29/08 — Tableau des routes (#10)**
`/equipements/:id/modifier` et `/seuils` ajoutées au tableau de `frontend/README.md`, avec `technicien` comme rôle requis (cohérent avec `peutIntervenir()`).

**29/08 — Version pysnmp cassée**
En écrivant les tests pytest de `network_collector.py`, découvert que `network/requirements.txt` épinglait `pysnmp==6.2.6` alors que le code importe `pysnmp.hlapi.v3arch.asyncio`, module introduit seulement en pysnmp 7.1.10+. L'agent réseau n'aurait jamais démarré en l'état (`ModuleNotFoundError`), un défaut que `python -m compileall` (vérif de syntaxe seule, sans import réel) ne pouvait pas détecter. Corrigé vers `pysnmp==7.1.29` (dernière version stable, testée : import + suite pytest complète passent).

**01/09 — Pollers réseau distribués / redondants (#157)**
Redondance simple retenue plutôt qu'une répartition multi-site : deux
instances de `network_collector.py`, une seule active à la fois
(`COLLECTOR_ROLE=primaire/secondaire` en `.env`). La primaire est active dès
le démarrage et expose un heartbeat HTTP local ; la secondaire reste en
veille et surveille ce heartbeat, puis prend le relais après
`FAILOVER_CYCLES_TOLERES` cycles sans réponse (pas de bascule automatique en
sens inverse, pour éviter les allers-retours). Côté backend, l'instance
active pousse un heartbeat (`POST /api/v1/collectors/heartbeat`, clé
partagée `app.collecteurs.cle-api`) que `CollecteurWatchdog` surveille selon
le même principe que `DisponibiliteWatchdog` pour les équipements (F3) :
silence prolongé → événement `collector_status_changed` sur `/ws/status`.
Cf. `agent/network/README.md` § Redondance pour la configuration complète.

## Points à trancher

**Sort de `/netvision-preview` (#11)**
Trois options possibles : retirer la route du build de prod, la documenter et garder en prod avec garde de rôle, ou la supprimer complètement. En attente d'arbitrage avant implémentation.
