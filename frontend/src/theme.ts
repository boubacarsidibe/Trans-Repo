export type Finition = "jour" | "nuit";

const CLE = "monitoring.finition";

/** Le panneau a deux finitions : plein jour, ou salle de supervision de nuit. */
export function finitionInitiale(): Finition {
	const memorisee = localStorage.getItem(CLE);
	if (memorisee === "jour" || memorisee === "nuit") return memorisee;
	return window.matchMedia("(prefers-color-scheme: dark)").matches ? "nuit" : "jour";
}

export function appliquerFinition(finition: Finition) {
	document.documentElement.dataset.theme = finition;
	localStorage.setItem(CLE, finition);
}
