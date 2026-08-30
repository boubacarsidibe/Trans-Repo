import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { BandeauSynoptique } from "../components/BandeauSynoptique";
import { Marque } from "../components/Marque";
import { ROLE, estAdministrateur, peutIntervenir } from "../supervision/libelles";
import { appliquerFinition, finitionInitiale, type Finition } from "../theme";

const ECRANS = [
	{ to: "/", libelle: "Synoptique", exact: true },
	{ to: "/equipements", libelle: "Équipements" },
	{ to: "/alertes", libelle: "Alertes" },
	// Lecture ouverte au technicien, écriture réservée à l'administrateur (§4.4).
	{ to: "/seuils", libelle: "Seuils", intervenant: true },
	{ to: "/rapports", libelle: "Rapports" },
	{ to: "/journal", libelle: "Journal", administrateur: true },
	{ to: "/utilisateurs", libelle: "Utilisateurs", administrateur: true },
];

export function AppLayout() {
	const { user, logout } = useAuth();
	const navigate = useNavigate();
	const [finition, setFinition] = useState<Finition>(finitionInitiale);
	const administrateur = estAdministrateur(user?.role);
	const intervenant = peutIntervenir(user?.role);

	function basculerFinition() {
		const suivante: Finition = finition === "jour" ? "nuit" : "jour";
		appliquerFinition(suivante);
		setFinition(suivante);
	}

	function seDeconnecter() {
		logout();
		navigate("/login");
	}

	return (
		<div className="cadre">
			<a className="saut-contenu" href="#contenu">
				Aller au contenu
			</a>

			<aside className="rail">
				<span className="rail-marque">
					<Marque />
					Supervision EPT
					<span className="rail-marque-faible">· CRI</span>
				</span>

				<nav className="rail-nav" aria-label="Écrans du poste">
					{ECRANS.filter(
						(ecran) => (!ecran.administrateur || administrateur) && (!ecran.intervenant || intervenant),
					).map((ecran) => (
						<NavLink
							key={ecran.to}
							to={ecran.to}
							end={ecran.exact}
							className={({ isActive }) => (isActive ? "rail-lien rail-lien-actif" : "rail-lien")}
						>
							{ecran.libelle}
						</NavLink>
					))}
				</nav>

				<div className="rail-poste">
					<span className="rail-identite">
						<span className="rail-identite-nom">{user?.username}</span>
						<span className="rail-identite-role">{user ? ROLE[user.role as keyof typeof ROLE] : ""}</span>
					</span>
					<button className="rail-bouton" type="button" onClick={basculerFinition}>
						{finition === "jour" ? "Nuit" : "Jour"}
						<span className="visuellement-masque"> — changer la finition du panneau</span>
					</button>
					<button className="rail-bouton" type="button" onClick={seDeconnecter}>
						Se déconnecter
					</button>
				</div>
			</aside>

			<div className="cadre-corps">
				<BandeauSynoptique />

				<main className="contenu" id="contenu">
					<Outlet />
				</main>
			</div>
		</div>
	);
}
