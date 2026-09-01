import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

// Police auto-hébergée : le déploiement du CRI vit sur l'intranet, aucune
// requête vers un CDN ne doit conditionner l'affichage du poste.
import "@fontsource-variable/inter/standard.css";

import "./styles/tokens.css";
import "./styles/base.css";
import "./styles/panneau.css";
import "./styles/pages.css";
import "./styles/connexion.css";

import App from "./App.tsx";

createRoot(document.getElementById("root")!).render(
	<StrictMode>
		<App />
	</StrictMode>,
);
