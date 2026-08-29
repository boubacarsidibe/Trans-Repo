# Journal de suivi

Historique des décisions techniques prises pendant le développement et des points qui restent à trancher, en complément des issues GitHub. Sert de trace pour le suivi de projet.

## Décisions actées

**29/08 — Rôles legacy (#8)**
`Role.CLIENT/ADMIN/MANAGER/OPERATOR` retirés, seuls `ADMINISTRATEUR/TECHNICIEN/OBSERVATEUR` subsistent (cohérent avec la matrice RBAC §4.4). `/api/auth/register` conservé mais restreint aux administrateurs plutôt que supprimé, pour ne pas casser l'API existante — la création de comptes reste aussi possible via `POST /api/v1/users`. `UserType` conservé : utilisé par plusieurs DTO/services, ce n'est pas du code mort. Fusionné dans `develop` (PR #53).

**29/08 — Duplicata `agent/` (#9)**
`agent/` (racine) confirmé comme version de référence (dernier commit plus récent, c'est celle que la CI compile). `backend_trans/agent/`, obsolète et non référencé ailleurs dans le dépôt, supprimé.

**29/08 — Tableau des routes (#10)**
`/equipements/:id/modifier` et `/seuils` ajoutées au tableau de `frontend/README.md`, avec `technicien` comme rôle requis (cohérent avec `peutIntervenir()`).

## Points à trancher

**Sort de `/netvision-preview` (#11)**
Trois options possibles : retirer la route du build de prod, la documenter et garder en prod avec garde de rôle, ou la supprimer complètement. En attente d'arbitrage avant implémentation.
