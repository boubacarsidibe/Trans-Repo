# Journal de suivi

Historique des décisions techniques prises pendant le développement et des points qui restent à trancher, en complément des issues GitHub. Sert de trace pour le suivi de projet.

## Décisions actées

**29/08 — Rôles legacy (#8)**
`Role.CLIENT/ADMIN/MANAGER/OPERATOR` retirés, seuls `ADMINISTRATEUR/TECHNICIEN/OBSERVATEUR` subsistent (cohérent avec la matrice RBAC §4.4). `/api/auth/register` conservé mais restreint aux administrateurs plutôt que supprimé, pour ne pas casser l'API existante — la création de comptes reste aussi possible via `POST /api/v1/users`. `UserType` conservé : utilisé par plusieurs DTO/services, ce n'est pas du code mort. Fusionné dans `develop` (PR #53).

**29/08 — Duplicata `agent/` (#9)**
`agent/` (racine) confirmé comme version de référence (dernier commit plus récent, c'est celle que la CI compile). `backend_trans/agent/`, obsolète et non référencé ailleurs dans le dépôt, supprimé.

## Points à trancher

_(aucun en cours)_
