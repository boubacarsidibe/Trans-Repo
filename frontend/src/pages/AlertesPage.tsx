import { useState } from "react";
import { Link } from "react-router-dom";
import { ActionsAlerte } from "../components/ActionsAlerte";
import { Lampe } from "../components/Lampe";
import { EtatVide, Message } from "../components/Retours";
import { useSupervision } from "../supervision/SupervisionContext";
import { SEVERITE, STATUT_ALERTE, TYPE_ANOMALIE } from "../supervision/libelles";
import { depuis, formatDateHeure } from "../supervision/format";
import type { Severite, StatutAlerte } from "../types/api";

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

export function AlertesPage() {
	const { alertes, chargement, erreur } = useSupervision();
	const [filtre, setFiltre] = useState<StatutAlerte | "toutes">("toutes");

	const visibles = alertes
		.filter((a) => filtre === "toutes" || a.statut === filtre)
		.sort((a, b) => b.dateDeclenchement.localeCompare(a.dateDeclenchement));

	return (
		<>
			<section className="section section-premiere">
				<div className="section-entete">
					<h1 className="plaque-titre">Journal des alertes</h1>
					<span className="donnee-faible">{visibles.length} entrées</span>
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
						{visibles.map((alerte) => (
							<div className="rangee rangee-alerte-large" key={alerte.id}>
								<Lampe
									etat={
										alerte.statut === "DECLENCHEE"
											? "alarme"
											: alerte.statut === "PRISE_EN_COMPTE"
												? "attention"
												: "actif"
									}
								/>
								<Link className="rangee-nom" to={`/equipements?poste=${alerte.equipementId}`}>
									{alerte.equipementNom}
								</Link>
								<span className="rangee-genre">{TYPE_ANOMALIE[alerte.typeAnomalie]}</span>
								<span className={CLASSE_SEVERITE[alerte.severite]}>{SEVERITE[alerte.severite]}</span>
								<span className="rangee-genre">
									{STATUT_ALERTE[alerte.statut]}
									{alerte.utilisateurPriseEnCharge && ` · ${alerte.utilisateurPriseEnCharge}`}
								</span>
								<span className="rangee-secondaire" title={formatDateHeure(alerte.dateDeclenchement)}>
									{depuis(alerte.dateDeclenchement)}
								</span>
								<ActionsAlerte alerte={alerte} />
							</div>
						))}
					</div>
				</div>
			</section>
		</>
	);
}
