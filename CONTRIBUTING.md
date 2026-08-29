# Conventions

## Branches

Jamais de push direct sur `develop` ou `main`. Une branche par sujet, créée
depuis `develop` à jour :

```
<type>/<description-courte>
```

Types : `feat` (fonctionnalité), `fix` (correction de bug), `chore` (tâche
de maintenance, config, nettoyage), `docs` (documentation), `test` (ajout de
tests), `debug` (investigation).

Exemples : `feat/rapports-pdf`, `fix/watchdog-faux-positifs`,
`docs/readme-backend`.

## Commits

```
<type>: <description au present, sans majuscule ni point final>
```

Un commit doit rester cohérent (une seule idée) ; pas besoin de découper à
l'excès pour autant. Exemples :

```
fix: corriger le calcul de disponibilite sur equipement supprime
docs: completer le tableau des routes du frontend
```

## Process

1. Créer la branche depuis `develop` à jour.
2. Développer, en lançant les vérifications localement (voir ci-dessous)
   avant de pousser.
3. Pousser la branche, ouvrir une pull request vers `develop`.
4. Attendre que la CI passe, relire le diff soi-même.
5. Fusionner dans `develop` (merge commit, pas de rebase de branche déjà
   poussée). `main` ne reçoit que `develop` une fois stabilisé.

## Vérifications avant de pousser

```bash
# backend
cd backend_trans && bash mvnw verify

# frontend
cd frontend && npm run build && npm run lint

# agents (pas de suite de tests pour l'instant : la CI verifie juste que ca compile)
cd agent && python -m compileall -q system network
```
