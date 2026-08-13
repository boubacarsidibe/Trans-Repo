import { useId, type ReactNode } from "react";

interface Props {
	libelle: string;
	aide?: string;
	children: (identifiant: string) => ReactNode;
}

/** Étiquette gravée au-dessus, saisie creusée dans la tôle en dessous. */
export function Champ({ libelle, aide, children }: Props) {
	const identifiant = useId();
	return (
		<div className="champ">
			<label className="champ-libelle" htmlFor={identifiant}>
				{libelle}
			</label>
			{children(identifiant)}
			{aide && <span className="champ-aide">{aide}</span>}
		</div>
	);
}
