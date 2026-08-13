import { useEffect, useState } from "react";
import { fetchJournal } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { RequireRole } from "../auth/RequireRole";
import { EtatVide, Message } from "../components/Retours";
import { ROLE, estAdministrateur } from "../supervision/libelles";
import { formatDateHeure } from "../supervision/format";
import type { EntreeJournal } from "../types/api";

export function JournalPage() {
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
	const [entrees, setEntrees] = useState<EntreeJournal[]>([]);
	const [chargement, setChargement] = useState(true);
	const [erreur, setErreur] = useState<string | null>(null);

	useEffect(() => {
		fetchJournal()
			.then(setEntrees)
			.catch(() => setErreur("Le journal d'audit n'a pas pu être lu."))
			.finally(() => setChargement(false));
	}, []);

	return (
		<section className="section section-premiere">
			<div className="section-entete">
				<h1 className="plaque-titre">Journal d'audit</h1>
				<span className="donnee-faible">{entrees.length} entrées</span>
			</div>

			{erreur && <Message ton="echec">{erreur}</Message>}

			<div className="encart">
				<div className="rangee rangee-journal rangee-entete" aria-hidden="true">
					<span>Horodatage</span>
					<span>Compte</span>
					<span>Action</span>
					<span>Source</span>
				</div>

				{chargement && <p className="chargement">Lecture du journal…</p>}

				{!chargement && !erreur && entrees.length === 0 && (
					<EtatVide titre="Journal vide">
						<p className="etat-vide-texte">
							Les connexions et les actions sur le parc s'inscrivent ici automatiquement.
						</p>
					</EtatVide>
				)}

				<div className="rangees">
					{entrees.map((entree) => (
						<div className="rangee rangee-journal" key={entree.id}>
							<span className="rangee-ip">{formatDateHeure(entree.horodatage)}</span>
							<span className="rangee-nom">{entree.utilisateurEmail}</span>
							<span className="rangee-secondaire">{entree.action}</span>
							<span className="rangee-ip">{entree.adresseIpSource ?? "—"}</span>
						</div>
					))}
				</div>
			</div>
		</section>
	);
}
