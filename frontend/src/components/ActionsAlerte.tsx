import { useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { prendreEnCompteAlerte, resoudreAlerte } from "../api/endpoints";
import { useSupervision } from "../supervision/SupervisionContext";
import { peutIntervenir } from "../supervision/libelles";
import type { Alerte } from "../types/api";

/**
 * Les deux gestes du métier. Le bouton et l'état qu'il produit portent le même
 * mot, et « Prendre en compte » éteint le clignotement de la lampe.
 */
export function ActionsAlerte({ alerte }: { alerte: Alerte }) {
	const { user } = useAuth();
	const { remplacerAlerte } = useSupervision();
	const [enCours, setEnCours] = useState(false);
	const [erreur, setErreur] = useState<string | null>(null);

	if (!peutIntervenir(user?.role) || alerte.statut === "RESOLUE") {
		return <span />;
	}

	async function agir(action: typeof prendreEnCompteAlerte) {
		setEnCours(true);
		setErreur(null);
		try {
			remplacerAlerte(await action(alerte.id));
		} catch {
			setErreur("Action refusée");
		} finally {
			setEnCours(false);
		}
	}

	if (erreur) {
		return <span className="etat etat-critique">{erreur}</span>;
	}

	return (
		<div className="rangee-actions">
			{alerte.statut === "DECLENCHEE" && (
				<button
					className="bouton bouton-menu"
					type="button"
					disabled={enCours}
					onClick={() => void agir(prendreEnCompteAlerte)}
				>
					Prendre en compte
				</button>
			)}
			<button
				className="bouton bouton-menu"
				type="button"
				disabled={enCours}
				onClick={() => void agir(resoudreAlerte)}
			>
				Résoudre
			</button>
		</div>
	);
}
