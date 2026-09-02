import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { BASE_URL } from "../api/client";
import { fetchAlertes, fetchEquipements } from "../api/endpoints";
import type { Alerte, Equipement } from "../types/api";
import { ouvrirCanaux, type DisponibiliteEvenement, type EvenementSupervision } from "./canalTempsReel";

/** Interrogation de repli quand le temps réel n'est pas établi. */
const INTERVALLE_REPLI_MS = 15_000;

/**
 * Filet de sécurité quand le temps réel fonctionne : une resynchronisation
 * espacée rattrape ce qu'une coupure brève aurait pu faire manquer, sans
 * remettre en place une interrogation permanente.
 */
const INTERVALLE_FILET_MS = 300_000;

/**
 * Une entrée du flux brut des événements (§8.3), telle que le journal en
 * direct l'affiche. `id` est généré à la réception : les événements n'en
 * portent pas côté serveur, et deux événements peuvent partager le même
 * horodatage à la milliseconde.
 */
export interface EntreeFlux {
	id: string;
	evenement: EvenementSupervision;
}

/** Le journal ne garde que les derniers événements : le reste défile hors vue. */
export const LIMITE_FLUX = 50;

interface SupervisionValue {
	equipements: Equipement[];
	alertes: Alerte[];
	chargement: boolean;
	erreur: string | null;
	derniereLecture: Date | null;
	tempsReel: boolean;
	/** Les `LIMITE_FLUX` derniers événements reçus, le plus récent en premier. */
	flux: EntreeFlux[];
	rafraichir: () => void;
	remplacerAlerte: (alerte: Alerte) => void;
}

const SupervisionContext = createContext<SupervisionValue | undefined>(undefined);

/**
 * Une seule source d'état du parc pour toute la console : le bandeau, les
 * relevés et les écrans lisent la même chose.
 *
 * <p>Les changements arrivent par les canaux WebSocket (§8). L'interrogation
 * périodique subsiste en repli — la perte du canal ne doit pas aveugler le
 * poste (§5.7).
 */
export function SupervisionProvider({ children }: { children: ReactNode }) {
	const [equipements, setEquipements] = useState<Equipement[]>([]);
	const [alertes, setAlertes] = useState<Alerte[]>([]);
	const [chargement, setChargement] = useState(true);
	const [erreur, setErreur] = useState<string | null>(null);
	const [derniereLecture, setDerniereLecture] = useState<Date | null>(null);
	const [tempsReel, setTempsReel] = useState(false);
	const [flux, setFlux] = useState<EntreeFlux[]>([]);
	const enCours = useRef(false);
	const compteurFlux = useRef(0);

	const lire = useCallback(async () => {
		if (enCours.current) return;
		enCours.current = true;
		try {
			const [parc, journal] = await Promise.all([fetchEquipements(), fetchAlertes()]);
			setEquipements(parc);
			setAlertes(journal);
			setDerniereLecture(new Date());
			setErreur(null);
		} catch {
			setErreur(`Le parc n'a pas répondu. Vérifiez que l'API est joignable sur ${BASE_URL}.`);
		} finally {
			enCours.current = false;
			setChargement(false);
		}
	}, []);

	const remplacerAlerte = useCallback((alerte: Alerte) => {
		setAlertes((actuelles) => actuelles.map((a) => (a.id === alerte.id ? alerte : a)));
	}, []);

	/** Une alerte reçue peut être nouvelle ou déjà connue : on fusionne par identifiant. */
	const fusionnerAlerte = useCallback((alerte: Alerte) => {
		setAlertes((actuelles) =>
			actuelles.some((a) => a.id === alerte.id)
				? actuelles.map((a) => (a.id === alerte.id ? alerte : a))
				: [alerte, ...actuelles],
		);
	}, []);

	const surEvenement = useCallback(
		(evenement: EvenementSupervision) => {
			setDerniereLecture(new Date());
			setFlux((actuels) =>
				[{ id: `evt-${compteurFlux.current++}`, evenement }, ...actuels].slice(0, LIMITE_FLUX),
			);

			switch (evenement.type) {
				case "alert_created":
				case "alert_updated":
				case "alert_acknowledged":
				case "alert_resolved":
					fusionnerAlerte(evenement.payload as Alerte);
					break;
				case "equipment_status_changed": {
					const statut = evenement.payload as DisponibiliteEvenement;
					setEquipements((actuels) =>
						actuels.map((e) =>
							e.id === statut.equipementId ? { ...e, derniereMesure: statut.derniereMesure } : e,
						),
					);
					break;
				}
				case "metric_update":
					// L'horodatage de dernière lecture suffit ici : les valeurs
					// détaillées sont chargées par l'écran qui les affiche.
					break;
			}
		},
		[fusionnerAlerte],
	);

	useEffect(() => ouvrirCanaux(surEvenement, setTempsReel), [surEvenement]);

	useEffect(() => {
		let minuteur: number | undefined;
		const intervalle = tempsReel ? INTERVALLE_FILET_MS : INTERVALLE_REPLI_MS;

		function programmer() {
			clearInterval(minuteur);
			if (document.hidden) return;
			// Relire au moment où le temps réel s'établit ferme la fenêtre entre
			// le premier chargement et l'ouverture des canaux.
			void lire();
			minuteur = window.setInterval(() => void lire(), intervalle);
		}

		programmer();
		document.addEventListener("visibilitychange", programmer);
		return () => {
			clearInterval(minuteur);
			document.removeEventListener("visibilitychange", programmer);
		};
	}, [lire, tempsReel]);

	return (
		<SupervisionContext.Provider
			value={{
				equipements,
				alertes,
				chargement,
				erreur,
				derniereLecture,
				tempsReel,
				flux,
				rafraichir: () => void lire(),
				remplacerAlerte,
			}}
		>
			{children}
		</SupervisionContext.Provider>
	);
}

export function useSupervision(): SupervisionValue {
	const contexte = useContext(SupervisionContext);
	if (!contexte) {
		throw new Error("useSupervision doit être utilisé dans un SupervisionProvider.");
	}
	return contexte;
}
