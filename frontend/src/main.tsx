import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

// Polices auto-hébergées : le déploiement du CRI vit sur l'intranet, aucune
// requête vers un CDN ne doit conditionner l'affichage du poste.
import "@fontsource-variable/archivo/wdth.css";
import "@fontsource/ibm-plex-mono/300.css";
import "@fontsource/ibm-plex-mono/400.css";

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
