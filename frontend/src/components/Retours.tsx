import type { ReactNode } from "react";

export function Chargement({ quoi }: { quoi: string }) {
	return (
		<p className="chargement" role="status">
			Lecture {quoi}…
		</p>
	);
}

export function Message({ ton = "neutre", children }: { ton?: "neutre" | "echec" | "succes"; children: ReactNode }) {
	const classes = ["message"];
	if (ton === "echec") classes.push("message-echec");
	if (ton === "succes") classes.push("message-succes");
	return (
		<p className={classes.join(" ")} role={ton === "echec" ? "alert" : "status"}>
			{children}
		</p>
	);
}

/** Un écran vide est une invitation à agir, pas un constat. */
export function EtatVide({ titre, children }: { titre: string; children?: ReactNode }) {
	return (
		<div className="etat-vide">
			<p className="etat-vide-titre">{titre}</p>
			{children}
		</div>
	);
}
