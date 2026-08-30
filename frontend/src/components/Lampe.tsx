import { TEINTE_ETAT, type EtatPoste } from "../supervision/etat";

const CLASSE_TEINTE = {
	verte: "lampe-verte",
	ambre: "lampe-ambre",
	rouge: "lampe-rouge",
	eteinte: "lampe-eteinte",
} as const;

interface Props {
	etat: EtatPoste;
	large?: boolean;
}

/**
 * Une lampe de signalisation. En alarme elle clignote jusqu'à la prise en
 * compte : le clignotement est l'état du travail, pas un effet.
 */
export function Lampe({ etat, large }: Props) {
	const classes = ["lampe", CLASSE_TEINTE[TEINTE_ETAT[etat]]];
	if (etat === "alarme") classes.push("lampe-clignote");
	if (large) classes.push("lampe-large");
	return <span className={classes.join(" ")} aria-hidden="true" />;
}
