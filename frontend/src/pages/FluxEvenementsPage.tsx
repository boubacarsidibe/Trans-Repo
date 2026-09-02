import { Link } from "react-router-dom";
import { Lampe } from "../components/Lampe";
import { EtatVide } from "../components/Retours";
import { LIMITE_FLUX, useSupervision } from "../supervision/SupervisionContext";
import { detailEvenement, equipementDuEvenement, etatEvenement, LIBELLE_TYPE_EVENEMENT } from "../supervision/flux";
import { formatDateHeure } from "../supervision/format";

/**
 * Le journal brut des trois canaux WebSocket (§8), au fil de l'eau : ce que
 * `SupervisionContext` agrège en état ne montre pas ce qui a été reçu, quand,
 * ni dans quel ordre. Cet écran l'expose tel quel, sans rien simuler.
 */
export function FluxEvenementsPage() {
	const { flux, alertes, equipements } = useSupervision();

	return (
		<section className="section section-premiere">
			<div className="section-entete">
				<h1 className="plaque-titre">Flux d'événements en direct</h1>
				<span className="donnee-faible">
					{flux.length} sur {LIMITE_FLUX} conservés
				</span>
			</div>

			<div className="encart">
				<div className="rangee rangee-flux rangee-entete" aria-hidden="true">
					<span />
					<span>Horodatage</span>
					<span>Type</span>
					<span>Équipement</span>
					<span>Détail</span>
				</div>

				{flux.length === 0 && (
					<EtatVide titre="Aucun événement reçu pour l'instant">
						<p className="etat-vide-texte">
							Dès qu'une métrique, une alerte ou une disponibilité change sur le parc
							{equipements.length > 0 || alertes.length > 0 ? "" : " supervisé"}, l'événement apparaît ici.
						</p>
					</EtatVide>
				)}

				<div className="rangees">
					{flux.map((entree) => {
						const equipement = equipementDuEvenement(entree.evenement);
						return (
							<div className="rangee rangee-flux" key={entree.id}>
								<Lampe etat={etatEvenement(entree.evenement)} />
								<span className="rangee-secondaire">{formatDateHeure(entree.evenement.horodatage)}</span>
								<span className="rangee-genre">{LIBELLE_TYPE_EVENEMENT[entree.evenement.type]}</span>
								{equipement ? (
									<Link className="rangee-nom" to={`/equipements?poste=${equipement.id}`}>
										{equipement.nom}
									</Link>
								) : (
									<span className="rangee-nom">—</span>
								)}
								<span className="rangee-secondaire">{detailEvenement(entree.evenement)}</span>
							</div>
						);
					})}
				</div>
			</div>
		</section>
	);
}
