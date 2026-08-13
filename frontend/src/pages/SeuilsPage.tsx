import { useEffect, useMemo, useState, type FormEvent } from "react";
import { createSeuil, fetchSeuils, supprimerSeuil, updateSeuil } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { RequireRole } from "../auth/RequireRole";
import { Champ } from "../components/Champ";
import { EtatVide, Message } from "../components/Retours";
import { useSupervision } from "../supervision/SupervisionContext";
import { ROLE, TYPE_METRIQUE, estAdministrateur, peutIntervenir } from "../supervision/libelles";
import type { SeuilAlerte, TypeMetrique } from "../types/api";

const TYPES_METRIQUE = Object.keys(TYPE_METRIQUE) as TypeMetrique[];

interface Saisie {
	typeMetrique: TypeMetrique;
	equipementId: string;
	avertissement: string;
	critique: string;
	dureeSecondes: string;
}

const SAISIE_VIDE: Saisie = {
	typeMetrique: "CPU",
	equipementId: "",
	avertissement: "",
	critique: "",
	dureeSecondes: "0",
};

export function SeuilsPage() {
	const { user } = useAuth();

	return (
		<RequireRole
			autorise={peutIntervenir(user?.role)}
			requis={user ? ROLE[user.role as keyof typeof ROLE] : "inconnu"}
		>
			<Contenu />
		</RequireRole>
	);
}

function Contenu() {
	const { user } = useAuth();
	const { equipements } = useSupervision();
	const administrateur = estAdministrateur(user?.role);

	const [seuils, setSeuils] = useState<SeuilAlerte[]>([]);
	const [chargement, setChargement] = useState(true);
	const [erreur, setErreur] = useState<string | null>(null);
	const [formulaire, setFormulaire] = useState(false);
	const [edition, setEdition] = useState<SeuilAlerte | null>(null);
	const [saisie, setSaisie] = useState<Saisie>(SAISIE_VIDE);
	const [envoi, setEnvoi] = useState(false);

	useEffect(() => {
		fetchSeuils()
			.then(setSeuils)
			.catch(() => setErreur("Les seuils n'ont pas pu être lus."))
			.finally(() => setChargement(false));
	}, []);

	const nomEquipement = useMemo(
		() => new Map(equipements.map((e) => [e.id, e.nom])),
		[equipements],
	);

	function ouvrirCreation() {
		setEdition(null);
		setSaisie(SAISIE_VIDE);
		setFormulaire(true);
	}

	function ouvrirEdition(seuil: SeuilAlerte) {
		setEdition(seuil);
		setSaisie({
			typeMetrique: seuil.typeMetrique,
			equipementId: seuil.equipementId ?? "",
			avertissement: seuil.avertissement === null ? "" : String(seuil.avertissement),
			critique: seuil.critique === null ? "" : String(seuil.critique),
			dureeSecondes: String(seuil.dureeSecondes),
		});
		setFormulaire(true);
	}

	async function soumettre(evenement: FormEvent) {
		evenement.preventDefault();
		setEnvoi(true);
		setErreur(null);

		const corps = {
			typeMetrique: saisie.typeMetrique,
			equipementId: saisie.equipementId === "" ? null : saisie.equipementId,
			avertissement: saisie.avertissement === "" ? null : Number(saisie.avertissement),
			critique: saisie.critique === "" ? null : Number(saisie.critique),
			dureeSecondes: Number(saisie.dureeSecondes || "0"),
		};

		try {
			if (edition) {
				const misAJour = await updateSeuil(edition.id, corps);
				setSeuils((actuels) => actuels.map((s) => (s.id === edition.id ? misAJour : s)));
			} else {
				const cree = await createSeuil(corps);
				setSeuils((actuels) => [...actuels, cree]);
			}
			setFormulaire(false);
			setEdition(null);
		} catch {
			setErreur(
				edition
					? "Le seuil n'a pas été modifié. Vérifiez que l'avertissement ne dépasse pas le critique."
					: "Le seuil n'a pas été créé. Il en existe peut-être déjà un pour cette métrique et ce périmètre.",
			);
		} finally {
			setEnvoi(false);
		}
	}

	async function supprimer(seuil: SeuilAlerte) {
		try {
			await supprimerSeuil(seuil.id);
			setSeuils((actuels) => actuels.filter((s) => s.id !== seuil.id));
		} catch {
			setErreur("Ce seuil n'a pas pu être supprimé.");
		}
	}

	return (
		<>
			<section className="section section-premiere">
				<div className="section-entete">
					<h1 className="plaque-titre">Seuils de déclenchement</h1>
					<span className="donnee-faible">{seuils.length} réglés</span>
					{administrateur && (
						<button className="bouton" type="button" onClick={formulaire ? () => setFormulaire(false) : ouvrirCreation}>
							{formulaire ? "Fermer" : "Ajouter un seuil"}
						</button>
					)}
				</div>

				<p className="donnee-faible">
					Un seuil sans équipement s'applique à tout le parc ; un seuil rattaché à un équipement le surcharge
					pour celui-ci seulement. La durée de maintien évite qu'une pointe d'une seconde déclenche une alerte.
				</p>

				{erreur && <Message ton="echec">{erreur}</Message>}

				{formulaire && administrateur && (
					<form className="encart formulaire" onSubmit={soumettre}>
						<div className="grille-champs">
							<Champ libelle="Métrique">
								{(champ) => (
									<select
										className="champ-saisie"
										id={champ}
										value={saisie.typeMetrique}
										disabled={edition !== null}
										onChange={(e) => setSaisie({ ...saisie, typeMetrique: e.target.value as TypeMetrique })}
									>
										{TYPES_METRIQUE.map((type) => (
											<option key={type} value={type}>
												{TYPE_METRIQUE[type]}
											</option>
										))}
									</select>
								)}
							</Champ>

							<Champ libelle="Portée" aide={edition ? "Non modifiable après création." : undefined}>
								{(champ) => (
									<select
										className="champ-saisie"
										id={champ}
										value={saisie.equipementId}
										disabled={edition !== null}
										onChange={(e) => setSaisie({ ...saisie, equipementId: e.target.value })}
									>
										<option value="">Défaut global</option>
										{equipements.map((equipement) => (
											<option key={equipement.id} value={equipement.id}>
												{equipement.nom}
											</option>
										))}
									</select>
								)}
							</Champ>

							<Champ libelle="Avertissement">
								{(champ) => (
									<input
										className="champ-saisie"
										id={champ}
										type="number"
										step="any"
										value={saisie.avertissement}
										onChange={(e) => setSaisie({ ...saisie, avertissement: e.target.value })}
									/>
								)}
							</Champ>

							<Champ libelle="Critique">
								{(champ) => (
									<input
										className="champ-saisie"
										id={champ}
										type="number"
										step="any"
										value={saisie.critique}
										onChange={(e) => setSaisie({ ...saisie, critique: e.target.value })}
									/>
								)}
							</Champ>

							<Champ libelle="Durée de maintien" aide="En secondes. 0 déclenche sur la mesure instantanée.">
								{(champ) => (
									<input
										className="champ-saisie"
										id={champ}
										type="number"
										min={0}
										step={30}
										value={saisie.dureeSecondes}
										onChange={(e) => setSaisie({ ...saisie, dureeSecondes: e.target.value })}
									/>
								)}
							</Champ>
						</div>

						<div className="formulaire-pied">
							<button className="bouton bouton-principal" type="submit" disabled={envoi}>
								{envoi ? "Enregistrement…" : edition ? "Enregistrer" : "Ajouter le seuil"}
							</button>
						</div>
					</form>
				)}
			</section>

			<section className="section">
				<div className="encart">
					<div className="rangee rangee-seuil rangee-entete" aria-hidden="true">
						<span>Métrique</span>
						<span>Portée</span>
						<span>Avertissement</span>
						<span>Critique</span>
						<span>Maintien</span>
						<span />
					</div>

					{chargement && <p className="chargement">Lecture des seuils…</p>}

					{!chargement && seuils.length === 0 && (
						<EtatVide titre="Aucun seuil réglé">
							<p className="etat-vide-texte">
								Sans seuil, aucune alerte de dépassement ne peut être levée.
							</p>
						</EtatVide>
					)}

					<div className="rangees">
						{seuils.map((seuil) => (
							<div className="rangee rangee-seuil" key={seuil.id}>
								<span className="rangee-nom">{TYPE_METRIQUE[seuil.typeMetrique]}</span>
								<span className="rangee-secondaire">
									{seuil.equipementId
										? (seuil.equipementNom ?? nomEquipement.get(seuil.equipementId) ?? "Équipement")
										: "Défaut global"}
								</span>
								<span className="donnee">{seuil.avertissement ?? "—"}</span>
								<span className="donnee">{seuil.critique ?? "—"}</span>
								<span className="rangee-secondaire">
									{seuil.dureeSecondes === 0 ? "Immédiat" : `${seuil.dureeSecondes} s`}
								</span>
								{administrateur ? (
									<span className="rangee-actions">
										<button className="bouton bouton-menu" type="button" onClick={() => ouvrirEdition(seuil)}>
											Modifier
										</button>
										{seuil.equipementId && (
											<button
												className="bouton bouton-menu"
												type="button"
												onClick={() => void supprimer(seuil)}
											>
												Supprimer
											</button>
										)}
									</span>
								) : (
									<span />
								)}
							</div>
						))}
					</div>
				</div>
			</section>
		</>
	);
}
