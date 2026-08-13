import type { ReactNode } from "react";
import { useAuth } from "./AuthContext";
import { EtatVide } from "../components/Retours";

/**
 * Le rôle décide de ce que le poste affiche. Un accès refusé se dit
 * franchement, avec le rôle qu'il aurait fallu.
 */
export function RequireRole({ autorise, requis, children }: { autorise: boolean; requis: string; children: ReactNode }) {
	const { user } = useAuth();

	if (!autorise) {
		return (
			<div className="encart">
				<EtatVide titre="Cet écran est réservé aux administrateurs">
					<p className="etat-vide-texte">
						Votre compte a le rôle {requis}. Demandez à un administrateur du CRI de vous l'attribuer si vous
						devez y accéder.
					</p>
					<p className="donnee-faible">{user?.email}</p>
				</EtatVide>
			</div>
		);
	}

	return <>{children}</>;
}
