import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Alerte, Equipement } from "../types/api";

vi.mock("../supervision/SupervisionContext", () => ({
	useSupervision: vi.fn(),
}));
vi.mock("../auth/AuthContext", () => ({
	useAuth: vi.fn(),
}));

import { useAuth } from "../auth/AuthContext";
import { useSupervision } from "../supervision/SupervisionContext";
import { SynoptiquePage } from "./SynoptiquePage";

const routeur: Equipement = {
	id: "eq-1",
	nom: "Routeur cœur",
	adresseIp: "10.0.0.1",
	type: "ROUTEUR",
	localisation: "Salle A",
	etat: "ACTIF",
	description: null,
	derniereMesure: "2026-08-29T08:00:00Z",
};

const serveur: Equipement = {
	id: "eq-2",
	nom: "Serveur web",
	adresseIp: "10.0.0.2",
	type: "SERVEUR",
	localisation: "Salle B",
	etat: "INACTIF",
	description: null,
	derniereMesure: null,
};

const alerteCritique: Alerte = {
	id: "al-1",
	equipementId: "eq-1",
	equipementNom: "Routeur cœur",
	typeAnomalie: "RESEAU",
	severite: "CRITIQUE",
	statut: "DECLENCHEE",
	dateDeclenchement: "2026-08-29T08:00:00Z",
	dateResolution: null,
	utilisateurPriseEnCharge: null,
};

describe("SynoptiquePage", () => {
	beforeEach(() => {
		vi.mocked(useSupervision).mockReturnValue({
			equipements: [routeur, serveur],
			alertes: [alerteCritique],
			chargement: false,
			erreur: null,
			derniereLecture: new Date("2026-08-29T08:05:00Z"),
			tempsReel: true,
			rafraichir: vi.fn(),
			remplacerAlerte: vi.fn(),
		});
		vi.mocked(useAuth).mockReturnValue({
			user: { id: 1, username: "obs", email: "obs@ept.sn", role: "OBSERVATEUR" },
			isAuthenticated: true,
			login: vi.fn(),
			logout: vi.fn(),
		});
	});

	it("affiche les relevés du parc", () => {
		render(
			<MemoryRouter>
				<SynoptiquePage />
			</MemoryRouter>,
		);

		expect(screen.getByText("1/2")).toBeInTheDocument();
		expect(screen.getByText("Équipements actifs")).toBeInTheDocument();
		expect(screen.getByText("Alertes ouvertes")).toBeInTheDocument();

		// "Hors ligne" apparaît aussi dans la légende : on vise le relevé chiffré
		// par son conteneur pour ne pas confondre les deux occurrences du texte.
		const releveHorsLigne = screen
			.getByText("Hors ligne", { selector: ".releve-libelle" })
			.closest(".releve") as HTMLElement;
		expect(within(releveHorsLigne).getByText("1")).toBeInTheDocument();
	});

	it("affiche la répartition des alertes par emplacement et par nature", () => {
		render(
			<MemoryRouter>
				<SynoptiquePage />
			</MemoryRouter>,
		);

		expect(screen.getByText("Routeur cœur")).toBeInTheDocument();

		const parEmplacement = screen.getByText("Par emplacement").closest("section") as HTMLElement;
		expect(within(parEmplacement).getByText("Salle A")).toBeInTheDocument();
		expect(within(parEmplacement).getByText("Salle B")).toBeInTheDocument();

		const parNature = screen.getByText("Par nature").closest("section") as HTMLElement;
		expect(within(parNature).getByText("Routeur")).toBeInTheDocument();
		expect(within(parNature).getByText("Serveur")).toBeInTheDocument();
	});
});
