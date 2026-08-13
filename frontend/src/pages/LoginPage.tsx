import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { BASE_URL } from "../api/client";
import { Champ } from "../components/Champ";
import { Marque } from "../components/Marque";
import { Message } from "../components/Retours";

export function LoginPage() {
	const { login } = useAuth();
	const navigate = useNavigate();
	const [email, setEmail] = useState("");
	const [motDePasse, setMotDePasse] = useState("");
	const [erreur, setErreur] = useState<string | null>(null);
	const [envoi, setEnvoi] = useState(false);

	async function ouvrirSession(evenement: FormEvent) {
		evenement.preventDefault();
		setErreur(null);
		setEnvoi(true);
		try {
			await login(email, motDePasse);
			navigate("/");
		} catch (cause) {
			// Deux pannes très différentes : dire laquelle, et quoi faire.
			const repondu = Boolean((cause as { response?: unknown })?.response);
			setErreur(
				repondu
					? "Adresse e-mail ou mot de passe incorrect."
					: `Le serveur ${BASE_URL} n'a pas répondu. Vérifiez qu'il est démarré.`,
			);
		} finally {
			setEnvoi(false);
		}
	}

	return (
		<div className="poste">
			<div className="poste-marque">
				<p className="poste-eyebrow">
					<Marque taille={15} />
					Centre des Ressources Informatiques
				</p>
				<h1 className="poste-nom">
					Supervision
					<br />
					EPT
				</h1>
				<div className="poste-regle" />
				<p className="poste-sous-titre">
					Parc réseau et serveurs de l'École Polytechnique de Thiès : état en temps réel, alertes et
					rapports.
				</p>
			</div>

			<form className="poste-bloc encart" onSubmit={ouvrirSession}>
				<h2 className="plaque-titre">Ouverture de session</h2>

				<div className="poste-champs">
					<Champ libelle="Adresse e-mail">
						{(id) => (
							<input
								className="champ-saisie"
								id={id}
								type="email"
								autoComplete="username"
								value={email}
								onChange={(e) => setEmail(e.target.value)}
								required
							/>
						)}
					</Champ>
					<Champ libelle="Mot de passe">
						{(id) => (
							<input
								className="champ-saisie"
								id={id}
								type="password"
								autoComplete="current-password"
								value={motDePasse}
								onChange={(e) => setMotDePasse(e.target.value)}
								required
							/>
						)}
					</Champ>
				</div>

				{erreur && <Message ton="echec">{erreur}</Message>}

				<button className="bouton bouton-principal bouton-large" type="submit" disabled={envoi}>
					{envoi ? "Connexion…" : "Se connecter"}
				</button>

				<div className="poste-pied">
					<span>Poste relié à {BASE_URL}</span>
					<span>Session de 15 min, renouvelée automatiquement.</span>
				</div>
			</form>
		</div>
	);
}
