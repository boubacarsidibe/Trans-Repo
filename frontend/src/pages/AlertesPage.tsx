import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { supprimerAlerte } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { ActionsAlerte } from "../components/ActionsAlerte";
import { Lampe } from "../components/Lampe";
import { Releve } from "../components/Releve";
import { EtatVide, Message } from "../components/Retours";
import { useSupervision } from "../supervision/SupervisionContext";
import { depuis, formatDateHeure } from "../supervision/format";
import { SEVERITE, STATUT_ALERTE, TYPE_ANOMALIE, estAdministrateur, peutIntervenir } from "../supervision/libelles";
import type { Alerte, Severite, StatutAlerte } from "../types/api";

const FILTRES: { cle: StatutAlerte | "toutes"; libelle: string }[] = [
	{ cle: "toutes", libelle: "Toutes" },
	{ cle: "DECLENCHEE", libelle: "Déclenchées" },
	{ cle: "PRISE_EN_COMPTE", libelle: "Prises en compte" },
	{ cle: "RESOLUE", libelle: "Résolues" },
];

const CLASSE_SEVERITE: Record<Severite, string> = {
	CRITIQUE: "etat etat-critique",
	AVERTISSEMENT: "etat etat-attention",
	INFO: "etat etat-calme",
};

/** La lampe suit le même code que partout ailleurs sur le poste. */
function etatAlerte(alerte: Alerte) {
	if (alerte.statut === "DECLENCHEE") return "alarme" as const;
	if (alerte.statut === "PRISE_EN_COMPTE") return "attention" as const;
	return "actif" as const;
}

export function AlertesPage() {
	const { alertes, chargement, erreur } = useSupervision();
	const [filtre, setFiltre] = useState<StatutAlerte | "toutes">("toutes");
	const [parametres, setParametres] = useSearchParams();

	const selectionnee = alertes.find((a) => a.id === parametres.get("alerte")) ?? null;

	const visibles = alertes
		.filter((a) => filtre === "toutes" || a.statut === filtre)
		.sort((a, b) => b.dateDeclenchement.localeCompare(a.dateDeclenchement));

	const critiques = alertes.filter((a) => a.severite === "CRITIQUE" && a.statut === "DECLENCHEE").length;
	const avertissements = alertes.filter((a) => a.severite === "AVERTISSEMENT" && a.statut === "DECLENCHEE").length;
	const prisesEnCompte = alertes.filter((a) => a.statut === "PRISE_EN_COMPTE").length;
	const resolues = alertes.filter((a) => a.statut === "RESOLUE").length;

	function selectionner(alerte: Alerte) {
		setParametres(alerte.id === selectionnee?.id ? {} : { alerte: alerte.id });
	}

	return (
		<>
			<section className="section section-premiere">
				<div className="section-entete">
					<h1 className="plaque-titre">Journal des alertes</h1>
					<span className="donnee-faible">{visibles.length} entrées</span>
				</div>

				<div className="releves-vitrees" style={{ marginBottom: 20 }}>
					<Releve valeur={critiques} libelle="Critiques en cours" alarme={critiques > 0} />
					<Releve valeur={avertissements} libelle="Avertissements en cours" />
					<Releve valeur={prisesEnCompte} libelle="Prises en compte" />
					<Releve valeur={resolues} libelle="Résolues" />
				</div>

				<div className="filtres">
					{FILTRES.map((choix) => (
						<button
							key={choix.cle}
							className={filtre === choix.cle ? "bouton bouton-menu bouton-actif" : "bouton bouton-menu"}
							type="button"
							onClick={() => setFiltre(choix.cle)}
						>
							{choix.libelle}
						</button>
					))}
				</div>
			</section>

			<section className="section">
				<div className="encart">
					<div className="rangee rangee-alerte-large rangee-entete" aria-hidden="true">
						<span />
						<span>Équipement</span>
						<span>Anomalie</span>
						<span>Sévérité</span>
						<span>Statut</span>
						<span>Déclenchée</span>
						<span />
					</div>

					{chargement && alertes.length === 0 && <p className="chargement">Lecture du journal…</p>}

					{erreur && alertes.length === 0 && (
						<div className="etat-vide">
							<Message ton="echec">{erreur}</Message>
						</div>
					)}

					{!chargement && !erreur && visibles.length === 0 && (
						<EtatVide titre={filtre === "toutes" ? "Aucune alerte" : "Aucune alerte dans ce filtre"}>
							<p className="etat-vide-texte">
								{filtre === "toutes"
									? "Le parc n'a franchi aucun seuil depuis la mise en service."
									: "Changez de filtre pour voir le reste du journal."}
							</p>
						</EtatVide>
					)}

					<div className="rangees">
						{visibles.map((alerte) => {
							const critique = alerte.severite === "CRITIQUE" && alerte.statut === "DECLENCHEE";
							const classes = ["rangee", "rangee-alerte-large", "rangee-cliquable"];
							if (alerte.id === selectionnee?.id) classes.push("rangee-active");
							if (critique) classes.push("rangee-critique");
							return (
								<button
									key={alerte.id}
									className={classes.join(" ")}
									type="button"
									onClick={() => selectionner(alerte)}
									aria-pressed={alerte.id === selectionnee?.id}
								>
									<Lampe etat={etatAlerte(alerte)} />
									<span className="rangee-nom">{alerte.equipementNom}</span>
									<span className="rangee-genre">{TYPE_ANOMALIE[alerte.typeAnomalie]}</span>
									<span className={CLASSE_SEVERITE[alerte.severite]}>{SEVERITE[alerte.severite]}</span>
									<span className="rangee-genre">
										{STATUT_ALERTE[alerte.statut]}
										{alerte.utilisateurPriseEnCharge && ` · ${alerte.utilisateurPriseEnCharge}`}
									</span>
									<span className="rangee-secondaire" title={formatDateHeure(alerte.dateDeclenchement)}>
										{depuis(alerte.dateDeclenchement)}
									</span>
									<span className="rangee-fleche" aria-hidden="true">
										›
									</span>
									<span className="visuellement-masque">{STATUT_ALERTE[alerte.statut]}</span>
								</button>
							);
						})}
					</div>
				</div>
			</section>

			{selectionnee && <Fiche alerte={selectionnee} />}
		</>
	);
}

function Fiche({ alerte }: { alerte: Alerte }) {
	const { user } = useAuth();
	const { rafraichir } = useSupervision();
	const [, setParametres] = useSearchParams();
	const [confirmationSuppression, setConfirmationSuppression] = useState(false);
	const [erreurSuppression, setErreurSuppression] = useState<string | null>(null);

	/** Suppression réelle de la ligne (issue #181) : le message de refus vient du backend, tel quel. */
	async function supprimer() {
		if (!confirmationSuppression) {
			setConfirmationSuppression(true);
			return;
		}
		try {
			await supprimerAlerte(alerte.id);
			rafraichir();
			setParametres({});
		} catch (cause) {
			const message = (cause as { response?: { data?: { message?: string } } })?.response?.data?.message;
			setErreurSuppression(message ?? "La suppression a été refusée.");
		}
	}

	return (
		<section className="section">
			<div className="section-entete">
				<h2 className="plaque-titre">
					{alerte.equipementNom} · {TYPE_ANOMALIE[alerte.typeAnomalie]}
				</h2>
				<span className="etat">
					<Lampe etat={etatAlerte(alerte)} />
					{STATUT_ALERTE[alerte.statut]}
				</span>
				{((peutIntervenir(user?.role) && alerte.statut !== "RESOLUE")
					|| (estAdministrateur(user?.role) && alerte.statut === "RESOLUE")) && (
					<div className="commandes">
						<ActionsAlerte alerte={alerte} />
						{estAdministrateur(user?.role) && alerte.statut === "RESOLUE" && (
							<button className="bouton bouton-menu" type="button" onClick={() => void supprimer()}>
								{confirmationSuppression ? "Confirmer la suppression" : "Supprimer"}
							</button>
						)}
					</div>
				)}
			</div>

			{erreurSuppression && <Message ton="echec">{erreurSuppression}</Message>}

			<div className="encart">
				<div className="fiche">
					<div className="fiche-case">
						<span className="fiche-libelle">Équipement</span>
						<Link className="fiche-valeur" to={`/equipements?poste=${alerte.equipementId}`}>
							{alerte.equipementNom}
						</Link>
					</div>
					<div className="fiche-case">
						<span className="fiche-libelle">Type d'anomalie</span>
						<span className="fiche-valeur">{TYPE_ANOMALIE[alerte.typeAnomalie]}</span>
					</div>
					<div className="fiche-case">
						<span className="fiche-libelle">Sévérité</span>
						<span className={CLASSE_SEVERITE[alerte.severite]}>{SEVERITE[alerte.severite]}</span>
					</div>
					<div className="fiche-case">
						<span className="fiche-libelle">Déclenchée le</span>
						<span className="fiche-valeur">{formatDateHeure(alerte.dateDeclenchement)}</span>
					</div>
					<div className="fiche-case">
						<span className="fiche-libelle">Prise en compte</span>
						<span className="fiche-valeur">
							{alerte.utilisateurPriseEnCharge ? `Par ${alerte.utilisateurPriseEnCharge}` : "Pas encore"}
						</span>
					</div>
					<div className="fiche-case">
						<span className="fiche-libelle">Résolue le</span>
						<span className="fiche-valeur">
							{alerte.dateResolution ? formatDateHeure(alerte.dateResolution) : "Pas encore"}
						</span>
					</div>
				</div>
			</div>
		</section>
	);
}
