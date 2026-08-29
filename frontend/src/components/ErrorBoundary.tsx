import { Component, type ErrorInfo, type ReactNode } from "react";
import { EtatVide } from "./Retours";

interface Props {
	children: ReactNode;
}

interface State {
	erreur: Error | null;
}

/**
 * Filet de sécurité pour toute exception de rendu imprévue : sans lui, une
 * erreur dans n'importe quel écran fait disparaître toute l'application côté
 * utilisateur, sans le moindre message.
 */
export class ErrorBoundary extends Component<Props, State> {
	state: State = { erreur: null };

	static getDerivedStateFromError(erreur: Error): State {
		return { erreur };
	}

	componentDidCatch(erreur: Error, info: ErrorInfo) {
		console.error("Erreur de rendu non interceptée :", erreur, info.componentStack);
	}

	render() {
		const { erreur } = this.state;
		if (!erreur) {
			return this.props.children;
		}

		return (
			<section className="section section-premiere">
				<div className="encart">
					<EtatVide titre="Ce poste a rencontré une erreur inattendue">
						<p className="etat-vide-texte">
							L'écran n'a pas pu s'afficher correctement. Rechargez la page ; si l'erreur persiste,
							signalez-la avec le message ci-dessous.
						</p>
						<p className="donnee-faible">{erreur.message}</p>
						<button
							className="bouton bouton-principal"
							type="button"
							onClick={() => window.location.reload()}
						>
							Recharger la page
						</button>
					</EtatVide>
				</div>
			</section>
		);
	}
}
