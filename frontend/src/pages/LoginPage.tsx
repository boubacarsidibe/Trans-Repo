import { useId, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { BASE_URL } from "../api/client";
import { IconeCadenas, IconeFleche, IconePersonne, IconeReseau } from "../components/IconesConnexion";
import { Message } from "../components/Retours";

export function LoginPage() {
	const { login } = useAuth();
	const navigate = useNavigate();
	const idEmail = useId();
	const idMotDePasse = useId();
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
		<div className="scene-connexion">
			<div className="scene-fond" aria-hidden="true">
				<div className="scene-lueur scene-lueur-a" />
				<div className="scene-lueur scene-lueur-b" />
			</div>

			<main className="carte-connexion">
				<div className="carte-entete">
					<div className="carte-insigne">
						<IconeReseau />
					</div>
					<h1 className="carte-nom">
						Supervision<span className="carte-nom-accent"> EPT</span>
					</h1>
					<p className="carte-sous-nom">Centre des Ressources Informatiques</p>
				</div>

				<div className="carte-verre">
					<div className="carte-verre-entete">
						<h2>Connexion</h2>
						<p>Accédez à votre console de supervision.</p>
					</div>

					<form className="carte-formulaire" onSubmit={ouvrirSession}>
						<div className="champ-verre">
							<label htmlFor={idEmail}>Adresse e-mail</label>
							<div className="champ-verre-saisie">
								<IconePersonne />
								<input
									id={idEmail}
									type="email"
									autoComplete="username"
									value={email}
									onChange={(e) => setEmail(e.target.value)}
									required
								/>
							</div>
						</div>

						<div className="champ-verre">
							<label htmlFor={idMotDePasse}>Mot de passe</label>
							<div className="champ-verre-saisie">
								<IconeCadenas />
								<input
									id={idMotDePasse}
									type="password"
									autoComplete="current-password"
									value={motDePasse}
									onChange={(e) => setMotDePasse(e.target.value)}
									required
								/>
							</div>
						</div>

						{erreur && <Message ton="echec">{erreur}</Message>}

						<button className="bouton-verre" type="submit" disabled={envoi}>
							<span>{envoi ? "Connexion…" : "Continuer"}</span>
							<IconeFleche />
						</button>
					</form>

					<div className="carte-pied">
						<span className="temoin-actif">
							<span className="temoin-point" />
							Poste relié à {BASE_URL}
						</span>
					</div>
				</div>
			</main>
		</div>
	);
}
