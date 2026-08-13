import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { archiveEquipement } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { Lampe } from "../components/Lampe";
import { EtatVide, Message } from "../components/Retours";
import { useSupervision } from "../supervision/SupervisionContext";
import { LIBELLE_ETAT, alerteOuverte, etatPoste, trierParGravite } from "../supervision/etat";
import { ETAT_EQUIPEMENT, TYPE_EQUIPEMENT, peutIntervenir } from "../supervision/libelles";
import type { Equipement } from "../types/api";
import { MetricChart } from "./MetricChart";

const FILTRES = [
	{ cle: "tous", libelle: "Tout le parc" },
	{ cle: "attention", libelle: "À traiter" },
	{ cle: "SERVEUR", libelle: "Serveurs" },
	{ cle: "ROUTEUR", libelle: "Routeurs" },
	{ cle: "SWITCH", libelle: "Commutateurs" },
	{ cle: "POINT_ACCES", libelle: "Points d'accès" },
];

export function EquipementsPage() {
	const { equipements, alertes, chargement, erreur } = useSupervision();
	const { user } = useAuth();
	const [parametres, setParametres] = useSearchParams();
	const [filtre, setFiltre] = useState("tous");
	const [recherche, setRecherche] = useState("");

	const ouvertes = alertes.filter(alerteOuverte);
	const selectionne = equipements.find((e) => e.id === parametres.get("poste")) ?? null;
	const terme = recherche.trim().toLowerCase();

	const visibles = trierParGravite(equipements, ouvertes).filter((poste) => {
		if (filtre === "attention" && etatPoste(poste, ouvertes) === "actif") return false;
		if (filtre !== "tous" && filtre !== "attention" && poste.type !== filtre) return false;
		if (!terme) return true;
		return [poste.nom, poste.adresseIp, poste.localisation ?? ""].some((champ) =>
			champ.toLowerCase().includes(terme),
		);
	});

	function selectionner(poste: Equipement) {
		setParametres(poste.id === selectionne?.id ? {} : { poste: poste.id });
	}

	return (
		<>
			<section className="section section-premiere">
				<div className="section-entete">
					<h1 className="plaque-titre">Équipements supervisés</h1>
					<span className="donnee-faible">
						{visibles.length} sur {equipements.length}
					</span>
					{peutIntervenir(user?.role) && (
						<Link className="bouton" to="/equipements/nouveau">
							Déclarer un équipement
						</Link>
					)}
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
					<input
						className="champ-saisie filtres-recherche"
						type="search"
						placeholder="Nom, IP, emplacement"
						aria-label="Filtrer le parc"
						value={recherche}
						onChange={(e) => setRecherche(e.target.value)}
					/>
				</div>
			</section>

			<section className="section">
				<div className="encart">
					<div className="rangee rangee-entete" aria-hidden="true">
						<span />
						<span>Nom</span>
						<span>Adresse IP</span>
						<span>Nature</span>
						<span>Emplacement</span>
						<span />
					</div>

					{chargement && equipements.length === 0 && <p className="chargement">Lecture du parc…</p>}

					{erreur && equipements.length === 0 && (
						<div className="etat-vide">
							<Message ton="echec">{erreur}</Message>
						</div>
					)}

					{!chargement && equipements.length === 0 && !erreur && (
						<EtatVide titre="Aucun équipement déclaré">
							<p className="etat-vide-texte">
								Déclarez un serveur, un routeur, un commutateur ou un point d'accès : la plateforme
								génère alors la clé que son agent utilisera pour pousser ses métriques.
							</p>
							{peutIntervenir(user?.role) && (
								<Link className="bouton bouton-principal" to="/equipements/nouveau">
									Déclarer un équipement
								</Link>
							)}
						</EtatVide>
					)}

					{equipements.length > 0 && visibles.length === 0 && (
						<EtatVide titre="Aucun équipement ne correspond">
							<p className="etat-vide-texte">Élargissez le filtre ou effacez la recherche.</p>
						</EtatVide>
					)}

					<div className="rangees">
						{visibles.map((poste) => {
							const etat = etatPoste(poste, ouvertes);
							return (
								<button
									key={poste.id}
									className={
										poste.id === selectionne?.id
											? "rangee rangee-cliquable rangee-active"
											: "rangee rangee-cliquable"
									}
									type="button"
									onClick={() => selectionner(poste)}
									aria-pressed={poste.id === selectionne?.id}
								>
									<Lampe etat={etat} />
									<span className="rangee-nom">{poste.nom}</span>
									<span className="rangee-ip">{poste.adresseIp}</span>
									<span className="rangee-genre">{TYPE_EQUIPEMENT[poste.type]}</span>
									<span className="rangee-secondaire">{poste.localisation ?? "—"}</span>
									<span className="rangee-fleche" aria-hidden="true">
										›
									</span>
									<span className="visuellement-masque">{LIBELLE_ETAT[etat]}</span>
								</button>
							);
						})}
					</div>
				</div>
			</section>

			{selectionne && <Fiche poste={selectionne} />}
		</>
	);
}

function Fiche({ poste }: { poste: Equipement }) {
	const { user } = useAuth();
	const { alertes, rafraichir } = useSupervision();
	const navigate = useNavigate();
	const [confirmation, setConfirmation] = useState(false);
	const [erreur, setErreur] = useState<string | null>(null);
	const etat = etatPoste(poste, alertes.filter(alerteOuverte));

	async function archiver() {
		if (!confirmation) {
			setConfirmation(true);
			return;
		}
		try {
			await archiveEquipement(poste.id);
			rafraichir();
			navigate("/equipements");
		} catch {
			setErreur("L'archivage a été refusé. Votre rôle ne permet peut-être pas cette action.");
		}
	}

	return (
		<section className="section">
			<div className="section-entete">
				<h2 className="plaque-titre">{poste.nom}</h2>
				<span className="etat">
					<Lampe etat={etat} />
					{LIBELLE_ETAT[etat]}
				</span>
				{peutIntervenir(user?.role) && (
					<div className="commandes">
						<Link className="bouton bouton-menu" to={`/equipements/${poste.id}/modifier`}>
							Modifier
						</Link>
						<button className="bouton bouton-menu" type="button" onClick={() => void archiver()}>
							{confirmation ? "Confirmer l'archivage" : "Archiver"}
						</button>
					</div>
				)}
			</div>

			{erreur && <Message ton="echec">{erreur}</Message>}

			<div className="encart">
				<div className="fiche">
					<div className="fiche-case">
						<span className="fiche-libelle">Adresse IP</span>
						<span className="fiche-valeur">{poste.adresseIp}</span>
					</div>
					<div className="fiche-case">
						<span className="fiche-libelle">Nature</span>
						<span className="fiche-valeur">{TYPE_EQUIPEMENT[poste.type]}</span>
					</div>
					<div className="fiche-case">
						<span className="fiche-libelle">État déclaré</span>
						<span className="fiche-valeur">{ETAT_EQUIPEMENT[poste.etat]}</span>
					</div>
					<div className="fiche-case">
						<span className="fiche-libelle">Emplacement</span>
						<span className="fiche-valeur">{poste.localisation ?? "Non renseigné"}</span>
					</div>
					<div className="fiche-case">
						<span className="fiche-libelle">Description</span>
						<span className="fiche-valeur">{poste.description ?? "—"}</span>
					</div>
				</div>

				<MetricChart equipementId={poste.id} equipementNom={poste.nom} />
			</div>
		</section>
	);
}
