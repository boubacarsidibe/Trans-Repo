import { Link } from "react-router-dom";
import { ActionsAlerte } from "../components/ActionsAlerte";
import { Lampe } from "../components/Lampe";
import { Releve } from "../components/Releve";
import { EtatVide, Message } from "../components/Retours";
import { useSupervision } from "../supervision/SupervisionContext";
import { alerteOuverte, etatPoste, grouperParEmplacement, trierParGravite } from "../supervision/etat";
import { SEVERITE, STATUT_ALERTE, TYPE_ANOMALIE, TYPE_EQUIPEMENT } from "../supervision/libelles";
import { depuis, formatDateHeure, formatHeureSeconde } from "../supervision/format";
import type { Severite, TypeEquipement } from "../types/api";

const CLASSE_SEVERITE: Record<Severite, string> = {
	CRITIQUE: "etat etat-critique",
	AVERTISSEMENT: "etat etat-attention",
	INFO: "etat etat-calme",
};

const RANG_SEVERITE: Record<Severite, number> = { CRITIQUE: 0, AVERTISSEMENT: 1, INFO: 2 };

export function SynoptiquePage() {
	const { equipements, alertes, erreur, derniereLecture } = useSupervision();

	const ouvertes = alertes
		.filter(alerteOuverte)
		.sort(
			(a, b) =>
				RANG_SEVERITE[a.severite] - RANG_SEVERITE[b.severite] ||
				b.dateDeclenchement.localeCompare(a.dateDeclenchement),
		);
	const actifs = equipements.filter((e) => e.etat === "ACTIF").length;
	const horsLigne = equipements.filter((e) => e.etat === "INACTIF").length;
	const declenchees = ouvertes.filter((a) => a.statut === "DECLENCHEE").length;

	const parNature = Object.keys(TYPE_EQUIPEMENT)
		.map((nature) => [nature as TypeEquipement, equipements.filter((e) => e.type === nature).length] as const)
		.filter(([, total]) => total > 0);

	return (
		<>
			<section className="section section-premiere">
				<div className="section-entete">
					<h1 className="plaque-titre">État du parc</h1>
				</div>
				<div className="releves-vitrees">
					<Releve valeur={`${actifs}/${equipements.length}`} libelle="Équipements actifs" />
					<Releve valeur={ouvertes.length} libelle="Alertes ouvertes" alarme={declenchees > 0} />
					<Releve valeur={horsLigne} libelle="Hors ligne" />
					<Releve
						valeur={derniereLecture ? formatHeureSeconde(derniereLecture) : "—"}
						libelle="Dernière lecture"
					/>
				</div>
			</section>

			{erreur && (
				<div className="section">
					<Message ton="echec">{erreur}</Message>
				</div>
			)}

			<section className="section">
				<div className="section-entete">
					<h2 className="plaque-titre">Alertes en cours</h2>
					<Link className="bouton bouton-menu" to="/alertes">
						Tout le journal
					</Link>
				</div>

				<div className="encart">
					{ouvertes.length === 0 ? (
						<EtatVide titre="Aucune alerte en cours">
							<p className="etat-vide-texte">
								Tout le parc est nominal
								{derniereLecture ? ` à ${formatHeureSeconde(derniereLecture)}` : ""}. Les alertes
								apparaissent ici dès qu'un seuil est franchi.
							</p>
						</EtatVide>
					) : (
						<div className="rangees">
							{ouvertes.map((alerte) => {
								const critique = alerte.severite === "CRITIQUE" && alerte.statut === "DECLENCHEE";
								const classes = critique ? "rangee rangee-alerte rangee-critique" : "rangee rangee-alerte";
								return (
									<div className={classes} key={alerte.id}>
										<Lampe etat={alerte.statut === "DECLENCHEE" ? "alarme" : "attention"} />
										<Link className="rangee-nom" to={`/equipements?poste=${alerte.equipementId}`}>
											{alerte.equipementNom}
										</Link>
										<span className="rangee-genre">{TYPE_ANOMALIE[alerte.typeAnomalie]}</span>
										<span className={CLASSE_SEVERITE[alerte.severite]}>
											{SEVERITE[alerte.severite]}
										</span>
										<span className="rangee-genre">
											{STATUT_ALERTE[alerte.statut]}
											{alerte.utilisateurPriseEnCharge && ` · ${alerte.utilisateurPriseEnCharge}`}
										</span>
										<span
											className="rangee-secondaire"
											title={formatDateHeure(alerte.dateDeclenchement)}
										>
											{depuis(alerte.dateDeclenchement)}
										</span>
										<ActionsAlerte alerte={alerte} />
									</div>
								);
							})}
						</div>
					)}
				</div>
			</section>

			<div className="grille-synoptique">
				<section>
					<div className="section-entete">
						<h2 className="plaque-titre">Par emplacement</h2>
					</div>

					<div className="encart">
						{equipements.length === 0 ? (
							<EtatVide titre="Parc vide">
								<p className="etat-vide-texte">
									Déclarez un premier équipement pour que le bandeau s'allume.
								</p>
								<Link className="bouton" to="/equipements/nouveau">
									Déclarer un équipement
								</Link>
							</EtatVide>
						) : (
							grouperParEmplacement(equipements, ouvertes).map(([emplacement, postes]) => (
								<div className="rangee-emplacement" key={emplacement}>
									<span className="emplacement-nom">{emplacement}</span>
									<span className="emplacement-lampes">
										{trierParGravite(postes, ouvertes).map((poste) => (
											<Lampe key={poste.id} etat={etatPoste(poste, ouvertes)} />
										))}
									</span>
								</div>
							))
						)}
					</div>
				</section>

				<section>
					<div className="section-entete">
						<h2 className="plaque-titre">Par nature</h2>
					</div>

					<div className="encart">
						{parNature.map(([nature, total]) => (
							<div className="rangee-emplacement" key={nature}>
								<span className="emplacement-nom">{TYPE_EQUIPEMENT[nature]}</span>
								<span className="donnee">{total}</span>
							</div>
						))}
						{parNature.length === 0 && <p className="chargement">Parc vide</p>}
					</div>

					<dl className="legende">
						<div className="legende-entree">
							<dt>
								<Lampe etat="actif" />
							</dt>
							<dd>Nominal</dd>
						</div>
						<div className="legende-entree">
							<dt>
								<Lampe etat="attention" />
							</dt>
							<dd>À surveiller</dd>
						</div>
						<div className="legende-entree">
							<dt>
								<Lampe etat="alarme" />
							</dt>
							<dd>En alarme — clignote jusqu'à la prise en compte</dd>
						</div>
						<div className="legende-entree">
							<dt>
								<Lampe etat="eteint" />
							</dt>
							<dd>Hors ligne</dd>
						</div>
					</dl>
				</section>
			</div>
		</>
	);
}
