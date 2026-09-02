import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { BandeauSynoptique } from "../components/BandeauSynoptique";
import { Marque } from "../components/Marque";
import { ROLE, estAdministrateur, peutIntervenir } from "../supervision/libelles";
import { appliquerFinition, finitionInitiale, type Finition } from "../theme";

/*
 * Sections façon panneau NOC : les mêmes écrans qu'avant, seulement groupés
 * par famille sous un petit label de section — la nav elle-même ne change
 * pas de route ni de garde d'accès.
 */
const SECTIONS = [
	{
		titre: "Vue d'ensemble",
		ecrans: [{ to: "/", libelle: "Synoptique", exact: true }],
	},
	{
		titre: "Infrastructure",
		ecrans: [
			{ to: "/equipements", libelle: "Équipements" },
			{ to: "/cartographie", libelle: "Cartographie" },
		],
	},
	{
		titre: "Supervision",
		ecrans: [
			{ to: "/alertes", libelle: "Alertes" },
			{ to: "/evenements", libelle: "Événements" },
			// Lecture ouverte au technicien, écriture réservée à l'administrateur (§4.4).
			{ to: "/seuils", libelle: "Seuils", intervenant: true },
			{ to: "/rapports", libelle: "Rapports" },
		],
	},
	{
		titre: "Administration",
		ecrans: [
			{ to: "/journal", libelle: "Journal", administrateur: true },
			{ to: "/utilisateurs", libelle: "Utilisateurs", administrateur: true },
		],
	},
] as const;

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
					{SECTIONS.map((section) => {
						const ecrans = section.ecrans.filter(
							(ecran) =>
								(!("administrateur" in ecran && ecran.administrateur) || administrateur) &&
								(!("intervenant" in ecran && ecran.intervenant) || intervenant),
						);
						if (ecrans.length === 0) return null;
						return (
							<div className="rail-section" key={section.titre}>
								<p className="rail-section-titre">{section.titre}</p>
								{ecrans.map((ecran) => (
									<NavLink
										key={ecran.to}
										to={ecran.to}
										end={"exact" in ecran ? ecran.exact : false}
										className={({ isActive }) => (isActive ? "rail-lien rail-lien-actif" : "rail-lien")}
									>
										{ecran.libelle}
									</NavLink>
								))}
							</div>
						);
					})}
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
