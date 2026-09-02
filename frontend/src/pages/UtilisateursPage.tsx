import { useEffect, useState, type FormEvent } from "react";
import {
	createUtilisateur,
	desactiverUtilisateur,
	fetchUtilisateurs,
	supprimerUtilisateurDefinitivement,
	updateUtilisateur,
} from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { RequireRole } from "../auth/RequireRole";
import { Champ } from "../components/Champ";
import { EtatVide, Message } from "../components/Retours";
import { ROLE, estAdministrateur } from "../supervision/libelles";
import { formatJour } from "../supervision/format";
import type { Role, Utilisateur } from "../types/api";

const ROLES_SUPERVISION: Role[] = ["ADMINISTRATEUR", "TECHNICIEN", "OBSERVATEUR"];

export function UtilisateursPage() {
	const { user } = useAuth();

	return (
		<RequireRole
			autorise={estAdministrateur(user?.role)}
			requis={user ? ROLE[user.role as keyof typeof ROLE] : "inconnu"}
		>
			<Contenu />
		</RequireRole>
	);
}

function Contenu() {
	const [comptes, setComptes] = useState<Utilisateur[]>([]);
	const [chargement, setChargement] = useState(true);
	const [erreur, setErreur] = useState<string | null>(null);
	const [formulaire, setFormulaire] = useState(false);
	const [saisie, setSaisie] = useState({ username: "", email: "", password: "", role: "OBSERVATEUR" as Role });
	const [envoi, setEnvoi] = useState(false);
	const [confirmationSuppressionId, setConfirmationSuppressionId] = useState<number | null>(null);

	useEffect(() => {
		fetchUtilisateurs()
			.then(setComptes)
			.catch(() => setErreur("La liste des comptes n'a pas pu être lue."))
			.finally(() => setChargement(false));
	}, []);

	async function creer(evenement: FormEvent) {
		evenement.preventDefault();
		setEnvoi(true);
		setErreur(null);
		try {
			const compte = await createUtilisateur(saisie);
			setComptes((actuels) => [...actuels, compte]);
			setSaisie({ username: "", email: "", password: "", role: "OBSERVATEUR" });
			setFormulaire(false);
		} catch {
			setErreur("Le compte n'a pas été créé. L'adresse est peut-être déjà utilisée.");
		} finally {
			setEnvoi(false);
		}
	}

	async function changerRole(compte: Utilisateur, role: Role) {
		try {
			const misAJour = await updateUtilisateur(compte.id, {
				username: compte.username,
				email: compte.email,
				role,
				active: compte.active,
			});
			setComptes((actuels) => actuels.map((c) => (c.id === compte.id ? misAJour : c)));
		} catch {
			setErreur(`Le rôle de ${compte.email} n'a pas pu être changé.`);
		}
	}

	async function desactiver(compte: Utilisateur) {
		try {
			await desactiverUtilisateur(compte.id);
			setComptes((actuels) => actuels.map((c) => (c.id === compte.id ? { ...c, active: false } : c)));
		} catch {
			setErreur(`Le compte ${compte.email} n'a pas pu être désactivé.`);
		}
	}

	/** Suppression réelle de la ligne (issue #179) : le message de refus vient du backend, tel quel. */
	async function supprimerDefinitivement(compte: Utilisateur) {
		if (confirmationSuppressionId !== compte.id) {
			setConfirmationSuppressionId(compte.id);
			return;
		}
		try {
			await supprimerUtilisateurDefinitivement(compte.id);
			setComptes((actuels) => actuels.filter((c) => c.id !== compte.id));
			setConfirmationSuppressionId(null);
		} catch (cause) {
			const message = (cause as { response?: { data?: { message?: string } } })?.response?.data?.message;
			setErreur(message ?? "La suppression définitive a été refusée.");
			setConfirmationSuppressionId(null);
		}
	}

	return (
		<>
			<section className="section section-premiere">
				<div className="section-entete">
					<h1 className="plaque-titre">Comptes du poste</h1>
					<span className="donnee-faible">{comptes.filter((c) => c.active).length} actifs</span>
					<button className="bouton" type="button" onClick={() => setFormulaire((ouvert) => !ouvert)}>
						{formulaire ? "Fermer" : "Créer un compte"}
					</button>
				</div>

				{erreur && <Message ton="echec">{erreur}</Message>}

				{formulaire && (
					<form className="encart formulaire" onSubmit={creer}>
						<div className="grille-champs">
							<Champ libelle="Nom affiché">
								{(champ) => (
									<input
										className="champ-saisie"
										id={champ}
										value={saisie.username}
										onChange={(e) => setSaisie({ ...saisie, username: e.target.value })}
										required
									/>
								)}
							</Champ>
							<Champ libelle="Adresse e-mail">
								{(champ) => (
									<input
										className="champ-saisie"
										id={champ}
										type="email"
										value={saisie.email}
										onChange={(e) => setSaisie({ ...saisie, email: e.target.value })}
										required
									/>
								)}
							</Champ>
							<Champ libelle="Mot de passe" aide="8 caractères minimum.">
								{(champ) => (
									<input
										className="champ-saisie"
										id={champ}
										type="password"
										autoComplete="new-password"
										minLength={8}
										value={saisie.password}
										onChange={(e) => setSaisie({ ...saisie, password: e.target.value })}
										required
									/>
								)}
							</Champ>
							<Champ libelle="Rôle">
								{(champ) => (
									<select
										className="champ-saisie"
										id={champ}
										value={saisie.role}
										onChange={(e) => setSaisie({ ...saisie, role: e.target.value as Role })}
									>
										{ROLES_SUPERVISION.map((role) => (
											<option key={role} value={role}>
												{ROLE[role]}
											</option>
										))}
									</select>
								)}
							</Champ>
						</div>
						<div className="formulaire-pied">
							<button className="bouton bouton-principal" type="submit" disabled={envoi}>
								{envoi ? "Création…" : "Créer le compte"}
							</button>
						</div>
					</form>
				)}
			</section>

			<section className="section">
				<div className="encart">
					<div className="rangee rangee-utilisateur rangee-entete" aria-hidden="true">
						<span>Nom</span>
						<span>Adresse e-mail</span>
						<span>Rôle</span>
						<span>Créé le</span>
						<span />
					</div>

					{chargement && <p className="chargement">Lecture des comptes…</p>}

					{!chargement && comptes.length === 0 && (
						<EtatVide titre="Aucun compte">
							<p className="etat-vide-texte">Créez le premier compte du poste de supervision.</p>
						</EtatVide>
					)}

					<div className="rangees">
						{comptes.map((compte) => (
							<div className="rangee rangee-utilisateur" key={compte.id}>
								<span className="rangee-nom">{compte.username}</span>
								<span className="rangee-ip">{compte.email}</span>
								<select
									className="champ-saisie rangee-select"
									aria-label={`Rôle de ${compte.email}`}
									value={compte.role}
									onChange={(e) => void changerRole(compte, e.target.value as Role)}
								>
									{ROLES_SUPERVISION.map((role) => (
										<option key={role} value={role}>
											{ROLE[role]}
										</option>
									))}
								</select>
								<span className="rangee-secondaire">{formatJour(compte.createdAt)}</span>
								<span className="rangee-actions">
									{compte.active ? (
										<button
											className="bouton bouton-menu"
											type="button"
											onClick={() => void desactiver(compte)}
										>
											Désactiver
										</button>
									) : (
										<span className="etat etat-calme">Désactivé</span>
									)}
									<button
										className="bouton bouton-menu"
										type="button"
										onClick={() => void supprimerDefinitivement(compte)}
									>
										{confirmationSuppressionId === compte.id
											? "Confirmer la suppression définitive"
											: "Supprimer définitivement"}
									</button>
								</span>
							</div>
						))}
					</div>
				</div>
			</section>
		</>
	);
}
