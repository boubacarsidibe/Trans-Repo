import { Link } from "react-router-dom";
import { Lampe } from "./Lampe";
import { useSupervision } from "../supervision/SupervisionContext";
import { LIBELLE_ETAT, alerteOuverte, etatPoste, grouperParEmplacement } from "../supervision/etat";
import { formatHeureSeconde } from "../supervision/format";

/**
 * Le bandeau synoptique : une lampe par équipement supervisé, groupée par
 * emplacement, présente sur tous les écrans. C'est le panneau mural — l'état
 * du parc reste lisible quelle que soit la page ouverte.
 */
export function BandeauSynoptique() {
	const { equipements, alertes, chargement, erreur, tempsReel, derniereLecture } = useSupervision();
	const ouvertes = alertes.filter(alerteOuverte);
	const groupes = grouperParEmplacement(equipements, ouvertes);
	let rang = 0;

	return (
		<div className="bandeau">
			{chargement && equipements.length === 0 && <p className="bandeau-vide">Interrogation du parc…</p>}

			{erreur && equipements.length === 0 && <p className="bandeau-vide">{erreur}</p>}

			{!chargement && !erreur && equipements.length === 0 && (
				<p className="bandeau-vide">Aucun équipement déclaré — le panneau est vide.</p>
			)}

			{groupes.map(([emplacement, postes]) => (
				<section className="bandeau-groupe" key={emplacement}>
					<h2 className="plaque-titre">{emplacement}</h2>
					<div className="bandeau-lampes">
						{postes.map((poste) => {
							const etat = etatPoste(poste, ouvertes);
							return (
								<Link
									className="douille"
									key={poste.id}
									to={`/equipements?poste=${poste.id}`}
									style={{ "--i": rang++ } as React.CSSProperties}
									title={`${poste.nom} — ${LIBELLE_ETAT[etat]}`}
								>
									<Lampe etat={etat} />
									<span className="douille-nom">{poste.nom}</span>
									<span className="visuellement-masque">{LIBELLE_ETAT[etat]}</span>
								</Link>
							);
						})}
					</div>
				</section>
			))}

			<p className="bandeau-liaison" title={tempsReel ? "Canaux WebSocket établis" : "Interrogation périodique de repli"}>
				<span className={tempsReel ? "liaison-temoin liaison-temoin-actif" : "liaison-temoin"} />
				{tempsReel ? "Temps réel" : "Repli 15 s"}
				{derniereLecture && <span className="liaison-heure">{formatHeureSeconde(derniereLecture)}</span>}
			</p>
		</div>
	);
}
