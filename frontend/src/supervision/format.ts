const dateHeure = new Intl.DateTimeFormat("fr-FR", {
	day: "2-digit",
	month: "2-digit",
	year: "numeric",
	hour: "2-digit",
	minute: "2-digit",
});

const heure = new Intl.DateTimeFormat("fr-FR", { hour: "2-digit", minute: "2-digit" });
const heureSeconde = new Intl.DateTimeFormat("fr-FR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
const jour = new Intl.DateTimeFormat("fr-FR", { day: "2-digit", month: "2-digit", year: "numeric" });
const relatif = new Intl.RelativeTimeFormat("fr", { numeric: "auto" });

export const formatDateHeure = (iso: string) => dateHeure.format(new Date(iso));
export const formatHeure = (iso: string) => heure.format(new Date(iso));
export const formatHeureSeconde = (date: Date) => heureSeconde.format(date);
export const formatJour = (iso: string) => jour.format(new Date(iso));

/** « il y a 6 min », « il y a 3 h » — l'échelle qu'un opérateur lit. */
export function depuis(iso: string): string {
	const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60_000);
	if (minutes < 1) return "à l'instant";
	if (minutes < 60) return relatif.format(-minutes, "minute");
	if (minutes < 1440) return relatif.format(-Math.round(minutes / 60), "hour");
	return relatif.format(-Math.round(minutes / 1440), "day");
}

export function formatValeur(valeur: number): string {
	if (Number.isInteger(valeur)) return String(valeur);
	return valeur.toFixed(Math.abs(valeur) < 10 ? 2 : 1);
}
