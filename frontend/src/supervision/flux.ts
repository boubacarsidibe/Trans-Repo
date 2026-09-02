import type { Alerte } from "../types/api";
import type { DisponibiliteEvenement, EvenementSupervision, MetriquesEvenement, TypeEvenement } from "./canalTempsReel";
import type { EtatPoste } from "./etat";
import { SEVERITE, STATUT_ALERTE, TYPE_ANOMALIE } from "./libelles";

/*
 * Ce que le flux en direct affiche pour chaque type d'événement (§8.3) : le
 * même vocabulaire que le reste de la console, pas un nouveau lexique.
 */

export const LIBELLE_TYPE_EVENEMENT: Record<TypeEvenement, string> = {
	metric_update: "Métrique",
	alert_created: "Alerte déclenchée",
	alert_updated: "Alerte modifiée",
	alert_acknowledged: "Alerte prise en compte",
	alert_resolved: "Alerte résolue",
	equipment_status_changed: "Disponibilité",
};

/** La lampe que le flux affiche pour un événement — le même code couleur que partout ailleurs. */
export function etatEvenement(evenement: EvenementSupervision): EtatPoste {
	switch (evenement.type) {
		case "alert_created":
		case "alert_updated": {
			const alerte = evenement.payload as Alerte;
			return alerte.statut === "DECLENCHEE" ? "alarme" : alerte.statut === "PRISE_EN_COMPTE" ? "attention" : "actif";
		}
		case "alert_acknowledged":
			return "attention";
		case "alert_resolved":
			return "actif";
		case "equipment_status_changed":
			return (evenement.payload as DisponibiliteEvenement).disponible ? "actif" : "eteint";
		case "metric_update":
			return "actif";
	}
}

/** L'équipement concerné par l'événement, quand la charge utile en porte un. */
export function equipementDuEvenement(evenement: EvenementSupervision): { id: string; nom: string } | null {
	switch (evenement.type) {
		case "metric_update": {
			const charge = evenement.payload as MetriquesEvenement;
			return { id: charge.equipementId, nom: charge.equipementNom };
		}
		case "alert_created":
		case "alert_updated":
		case "alert_acknowledged":
		case "alert_resolved": {
			const alerte = evenement.payload as Alerte;
			return { id: alerte.equipementId, nom: alerte.equipementNom };
		}
		case "equipment_status_changed": {
			const statut = evenement.payload as DisponibiliteEvenement;
			return { id: statut.equipementId, nom: statut.nom };
		}
		default:
			return null;
	}
}

/** Le détail court affiché sur la ligne du flux, propre à chaque type d'événement. */
export function detailEvenement(evenement: EvenementSupervision): string {
	switch (evenement.type) {
		case "metric_update": {
			const charge = evenement.payload as MetriquesEvenement;
			return `${charge.metriques.length} métrique${charge.metriques.length > 1 ? "s" : ""} remontée${charge.metriques.length > 1 ? "s" : ""}`;
		}
		case "alert_created":
		case "alert_updated": {
			const alerte = evenement.payload as Alerte;
			return `${TYPE_ANOMALIE[alerte.typeAnomalie]} · ${SEVERITE[alerte.severite]}`;
		}
		case "alert_acknowledged": {
			const alerte = evenement.payload as Alerte;
			return alerte.utilisateurPriseEnCharge
				? `Par ${alerte.utilisateurPriseEnCharge}`
				: STATUT_ALERTE.PRISE_EN_COMPTE;
		}
		case "alert_resolved": {
			const alerte = evenement.payload as Alerte;
			return `${TYPE_ANOMALIE[alerte.typeAnomalie]} résolue`;
		}
		case "equipment_status_changed": {
			const statut = evenement.payload as DisponibiliteEvenement;
			return statut.disponible ? "Disponible" : "Indisponible";
		}
		default:
			return "—";
	}
}
