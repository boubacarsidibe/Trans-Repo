import type { Alerte, Equipement } from "../types/api";

/** Ce qu'une lampe du bandeau peut dire d'un équipement. */
export type EtatPoste = "alarme" | "attention" | "eteint" | "actif";

const GRAVITE: Record<EtatPoste, number> = { alarme: 0, attention: 1, eteint: 2, actif: 3 };

export function etatPoste(equipement: Equipement, alertes: Alerte[]): EtatPoste {
	const siennes = alertes.filter((a) => a.equipementId === equipement.id);
	if (siennes.some((a) => a.statut === "DECLENCHEE")) return "alarme";
	if (equipement.etat === "INACTIF") return "eteint";
	if (equipement.etat === "EN_MAINTENANCE") return "attention";
	if (siennes.some((a) => a.statut === "PRISE_EN_COMPTE")) return "attention";
	return "actif";
}

/** Le panneau se lit par gravité : ce qui alarme passe devant. */
export function gravite(etat: EtatPoste): number {
	return GRAVITE[etat];
}

export const TEINTE_ETAT: Record<EtatPoste, "verte" | "ambre" | "rouge" | "eteinte"> = {
	alarme: "rouge",
	attention: "ambre",
	eteint: "eteinte",
	actif: "verte",
};

export const LIBELLE_ETAT: Record<EtatPoste, string> = {
	alarme: "En alarme",
	attention: "À surveiller",
	eteint: "Hors ligne",
	actif: "Nominal",
};

export function trierParGravite(equipements: Equipement[], alertes: Alerte[]): Equipement[] {
	return [...equipements].sort((a, b) => {
		const ecart = gravite(etatPoste(a, alertes)) - gravite(etatPoste(b, alertes));
		if (ecart !== 0) return ecart;
		const emplacement = (a.localisation ?? "").localeCompare(b.localisation ?? "", "fr");
		return emplacement !== 0 ? emplacement : a.nom.localeCompare(b.nom, "fr");
	});
}

/**
 * Groupé par emplacement, et les emplacements eux-mêmes classés par gravité :
 * un panneau se lit en commençant par ce qui va mal, jamais par l'alphabet.
 */
export function grouperParEmplacement(
	equipements: Equipement[],
	alertes: Alerte[] = [],
): [string, Equipement[]][] {
	const groupes = new Map<string, Equipement[]>();
	for (const equipement of equipements) {
		const cle = equipement.localisation?.trim() || "Emplacement non renseigné";
		groupes.set(cle, [...(groupes.get(cle) ?? []), equipement]);
	}

	const pire = (postes: Equipement[]) => Math.min(...postes.map((p) => gravite(etatPoste(p, alertes))));

	return [...groupes.entries()].sort(
		(a, b) =>
			pire(a[1]) - pire(b[1]) || b[1].length - a[1].length || a[0].localeCompare(b[0], "fr"),
	);
}

export const alerteOuverte = (a: Alerte) => a.statut !== "RESOLUE";
