import { BASE_URL, getToken } from "../api/client";

export type TypeEvenement =
	| "metric_update"
	| "alert_created"
	| "alert_updated"
	| "alert_acknowledged"
	| "alert_resolved"
	| "equipment_status_changed";

export interface EvenementSupervision<P = unknown> {
	type: TypeEvenement;
	horodatage: string;
	payload: P;
}

/** Les trois canaux du cahier de spécifications §8.2. */
const CANAUX = ["/ws/metrics", "/ws/alerts", "/ws/status"] as const;

const DELAI_RECONNEXION_INITIAL_MS = 1_000;
const DELAI_RECONNEXION_MAX_MS = 30_000;

/**
 * L'API WebSocket du navigateur ne permet pas de poser d'en-tête à l'ouverture :
 * le jeton passe donc en paramètre de requête, ce que le handshake côté serveur
 * accepte explicitement (§8.4).
 */
function urlDuCanal(chemin: string, jeton: string): string {
	const url = new URL(BASE_URL, window.location.origin);
	url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
	url.pathname = chemin;
	url.search = "";
	url.searchParams.set("token", jeton);
	return url.toString();
}

/**
 * Ouvre les trois canaux et les maintient ouverts. Rend une fonction d'arrêt.
 *
 * `surEtat` ne passe à `true` que lorsque les trois canaux sont établis : tant
 * qu'il en manque un, l'appelant doit conserver son interrogation périodique de
 * repli, sans quoi une partie des changements passerait inaperçue.
 */
export function ouvrirCanaux(
	surEvenement: (evenement: EvenementSupervision) => void,
	surEtat: (tempsReelActif: boolean) => void,
): () => void {
	const jeton = getToken();
	if (!jeton) {
		surEtat(false);
		return () => {};
	}

	let arrete = false;
	const sockets = new Map<string, WebSocket>();
	const minuteurs = new Map<string, number>();
	const delais = new Map<string, number>(CANAUX.map((c) => [c, DELAI_RECONNEXION_INITIAL_MS]));

	function publierEtat() {
		surEtat(!arrete && sockets.size === CANAUX.length);
	}

	function programmerReconnexion(chemin: string) {
		if (arrete) return;

		const delai = delais.get(chemin) ?? DELAI_RECONNEXION_INITIAL_MS;
		delais.set(chemin, Math.min(delai * 2, DELAI_RECONNEXION_MAX_MS));
		minuteurs.set(
			chemin,
			window.setTimeout(() => connecter(chemin), delai),
		);
	}

	function connecter(chemin: string) {
		if (arrete) return;

		const socket = new WebSocket(urlDuCanal(chemin, jeton as string));

		socket.onopen = () => {
			if (arrete) {
				socket.close();
				return;
			}
			delais.set(chemin, DELAI_RECONNEXION_INITIAL_MS);
			sockets.set(chemin, socket);
			publierEtat();
		};

		socket.onmessage = (message) => {
			try {
				surEvenement(JSON.parse(message.data as string) as EvenementSupervision);
			} catch {
				// Message illisible : on l'ignore plutôt que de casser le flux.
			}
		};

		socket.onclose = () => {
			sockets.delete(chemin);
			publierEtat();
			programmerReconnexion(chemin);
		};

		// `onclose` suit systématiquement `onerror` : la reconnexion y est traitée.
		socket.onerror = () => socket.close();
	}

	CANAUX.forEach(connecter);

	return () => {
		arrete = true;
		minuteurs.forEach((minuteur) => window.clearTimeout(minuteur));
		minuteurs.clear();
		sockets.forEach((socket) => socket.close());
		sockets.clear();
		surEtat(false);
	};
}
