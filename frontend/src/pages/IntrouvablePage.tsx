import { Link } from "react-router-dom";
import { EtatVide } from "../components/Retours";

export function IntrouvablePage() {
	return (
		<section className="section section-premiere">
			<div className="encart">
				<EtatVide titre="Cette page n'existe pas sur ce poste">
					<p className="etat-vide-texte">
						Le synoptique rassemble l'état de tout le parc — c'est le meilleur endroit d'où repartir.
					</p>
					<Link className="bouton bouton-principal" to="/">
						Ouvrir le synoptique
					</Link>
				</EtatVide>
			</div>
		</section>
	);
}
