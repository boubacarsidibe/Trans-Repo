import { useEffect, useState, type FormEvent } from "react";
import { fetchRapports, genererRapport, telechargerRapport, telechargerRapportCsv } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { Champ } from "../components/Champ";
import { EtatVide, Message } from "../components/Retours";
import { TYPE_RAPPORT, peutIntervenir } from "../supervision/libelles";
import { formatDateHeure, formatJour } from "../supervision/format";
import type { Rapport, TypeRapport } from "../types/api";

export function RapportsPage() {
	const { user } = useAuth();
	const [rapports, setRapports] = useState<Rapport[]>([]);
	const [chargement, setChargement] = useState(true);
	const [erreur, setErreur] = useState<string | null>(null);
	const [type, setType] = useState<TypeRapport>("JOURNALIER");
	const [envoi, setEnvoi] = useState(false);

	useEffect(() => {
		fetchRapports()
			.then(setRapports)
			.catch(() => setErreur("Les rapports n'ont pas pu être lus."))
			.finally(() => setChargement(false));
	}, []);

	async function ouvrir(id: string) {
		setErreur(null);
		try {
			// Le PDF transite par l'API authentifiée : on ne peut pas pointer un
			// lien directement dessus, il faut passer par une URL d'objet locale.
			const pdf = await telechargerRapport(id);
			const url = URL.createObjectURL(pdf);
			window.open(url, "_blank", "noopener");
			// Libérée après ouverture : l'onglet a déjà chargé le document.
			setTimeout(() => URL.revokeObjectURL(url), 60_000);
		} catch {
			setErreur("Le fichier de ce rapport est introuvable sur le serveur.");
		}
	}

	async function telechargerCsv(rapport: Rapport) {
		setErreur(null);
		try {
			const csv = await telechargerRapportCsv(rapport.id);
			const url = URL.createObjectURL(csv);
			const lien = document.createElement("a");
			lien.href = url;
			lien.download = `rapport-${rapport.typeRapport.toLowerCase()}-${rapport.id}.csv`;
			lien.click();
			setTimeout(() => URL.revokeObjectURL(url), 60_000);
		} catch {
			setErreur("Le CSV de ce rapport n'a pas pu être généré.");
		}
	}

	async function generer(evenement: FormEvent) {
		evenement.preventDefault();
		setEnvoi(true);
		setErreur(null);
		try {
			const rapport = await genererRapport({ typeRapport: type });
			setRapports((actuels) => [rapport, ...actuels]);
		} catch {
			setErreur("La génération a été refusée. Seuls les techniciens et administrateurs peuvent la lancer.");
		} finally {
			setEnvoi(false);
		}
	}

	return (
		<>
			<section className="section section-premiere">
				<div className="section-entete">
					<h1 className="plaque-titre">Rapports</h1>
					<span className="donnee-faible">{rapports.length} archivés</span>
				</div>

				{peutIntervenir(user?.role) && (
					<form className="encart formulaire" onSubmit={generer}>
						<div className="grille-champs">
							<Champ libelle="Période" aide="Le serveur borne lui-même la période demandée.">
								{(champ) => (
									<select
										className="champ-saisie"
										id={champ}
										value={type}
										onChange={(e) => setType(e.target.value as TypeRapport)}
									>
										{Object.entries(TYPE_RAPPORT).map(([valeur, libelle]) => (
											<option key={valeur} value={valeur}>
												{libelle}
											</option>
										))}
									</select>
								)}
							</Champ>
						</div>
						<div className="formulaire-pied">
							<button className="bouton bouton-principal" type="submit" disabled={envoi}>
								{envoi ? "Génération…" : "Générer le rapport"}
							</button>
						</div>
					</form>
				)}
			</section>

			{erreur && (
				<div className="section">
					<Message ton="echec">{erreur}</Message>
				</div>
			)}

			<section className="section">
				<div className="encart">
					<div className="rangee rangee-rapport rangee-entete" aria-hidden="true">
						<span>Période</span>
						<span>Couverture</span>
						<span>Généré le</span>
						<span />
					</div>

					{chargement && <p className="chargement">Lecture des rapports…</p>}

					{!chargement && rapports.length === 0 && (
						<EtatVide titre="Aucun rapport archivé">
							<p className="etat-vide-texte">
								Un rapport journalier est produit chaque nuit. Vous pouvez aussi en lancer un à la
								demande ci-dessus.
							</p>
						</EtatVide>
					)}

					<div className="rangees">
						{rapports.map((rapport) => (
							<div className="rangee rangee-rapport" key={rapport.id}>
								<span className="rangee-genre">{TYPE_RAPPORT[rapport.typeRapport]}</span>
								<span className="rangee-ip">
									{formatJour(rapport.periodeDebut)} → {formatJour(rapport.periodeFin)}
								</span>
								<span className="rangee-secondaire">{formatDateHeure(rapport.dateGeneration)}</span>
								<div className="rangee-actions">
									{rapport.fichierDisponible ? (
										<button
											className="bouton bouton-menu"
											type="button"
											onClick={() => void ouvrir(rapport.id)}
										>
											Ouvrir le PDF
										</button>
									) : (
										<span className="etat etat-calme">PDF pas encore produit</span>
									)}
									<button
										className="bouton bouton-menu"
										type="button"
										onClick={() => void telechargerCsv(rapport)}
									>
										CSV
									</button>
								</div>
							</div>
						))}
					</div>
				</div>

				<p className="champ-aide" style={{ marginTop: 12 }}>
					Le CSV est régénéré à la demande à partir des indicateurs de la période ; si l'écriture du PDF a
					échoué, le rapport reste consultable sans fichier PDF joint.
				</p>
			</section>
		</>
	);
}
