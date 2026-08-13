interface Props {
	valeur: string | number;
	unite?: string;
	libelle: string;
	alarme?: boolean;
}

/**
 * Un relevé. Le plus gros caractère de la page est toujours une valeur
 * machine : les titres nomment, les nombres parlent.
 */
export function Releve({ valeur, unite, libelle, alarme }: Props) {
	return (
		<div className={alarme ? "releve releve-alarme" : "releve"}>
			<span className="releve-valeur">
				{valeur}
				{unite && <span className="releve-unite">{unite}</span>}
			</span>
			<span className="releve-libelle">{libelle}</span>
		</div>
	);
}
