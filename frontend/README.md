# Console de supervision — EPT / CRI

Interface React de la plateforme de supervision du parc réseau et des serveurs de
l'École Polytechnique de Thiès. Elle consomme l'API Spring Boot de `backend_trans/`.

## Lancer

```bash
npm install
npm run dev            # http://localhost:5173
```

L'URL de l'API se règle dans `.env` (`VITE_API_BASE_URL`, `http://localhost:8080`
par défaut).

### Sans backend

```bash
VITE_MOCK_API=1 npm run dev
```

Un parc de démonstration (12 équipements, alertes dans chaque état, métriques)
est servi depuis `src/api/mocks.ts`, sans Spring Boot ni PostgreSQL. N'importe
quelle adresse ouvre une session ; le rôle est déduit du préfixe de l'e-mail —
`obs…` ouvre en observateur, `tech…` en technicien, tout le reste en
administrateur, ce qui permet de vérifier les écrans réservés.

Ce module n'est chargé que lorsque le drapeau vaut `1` : il ne part pas dans un
build normal.

## Écrans

| Route | Rôle requis | Contenu |
|---|---|---|
| `/login` | — | Ouverture de session |
| `/` | tous | Synoptique : relevés du parc, alertes en cours, répartition |
| `/equipements` | tous | Parc, fiche, enregistreur de métriques |
| `/equipements/nouveau` | technicien | Déclaration + clé d'agent |
| `/alertes` | tous | Journal, prise en compte et résolution |
| `/rapports` | tous | Rapports archivés, génération à la demande |
| `/journal` | administrateur | Journal d'audit |
| `/utilisateurs` | administrateur | Comptes du poste |
| `/netvision-preview` | administrateur | Écran de style/démo (issue #11), hors périmètre fonctionnel — gardé pour la démonstration du PFE |

## Conception

L'interface est un **panneau synoptique** : tôle émaillée, étiquettes gravées,
lampes de signalisation encastrées. La couleur n'existe que dans les lampes ; le
reste est gris, réglé à la hairline. Le bandeau en haut de chaque écran porte une
lampe par équipement, groupée par emplacement — une alerte déclenchée clignote
jusqu'à sa prise en compte.

Deux finitions : `jour` et `nuit`, choisies dans le rail (la préférence système
sert de valeur initiale). Les jetons vivent dans `src/styles/tokens.css` ; ne pas
écrire de couleur en dur ailleurs.

Polices auto-hébergées (`@fontsource`) : **Archivo** pour les étiquettes gravées,
**IBM Plex Mono** pour toute valeur machine. Le déploiement du CRI est
intranet-first, aucune police n'est chargée depuis un CDN.

## Vérifier

```bash
npm run build          # tsc -b puis vite build
npm run lint           # oxlint
npm run test           # vitest (Testing Library)
```

Tests unitaires (Vitest + Testing Library, `src/**/*.test.tsx`) sur les
écrans critiques : `LoginPage`, `EquipementsPage`, `AlertesPage`. Les
contextes (`AuthContext`, `SupervisionContext`) et `api/endpoints` sont
mockés — pas de backend ni de réseau requis.
