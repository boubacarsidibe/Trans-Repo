import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Equipement } from "../types/api";

vi.mock("../supervision/SupervisionContext", () => ({
	useSupervision: vi.fn(),
}));
vi.mock("../auth/AuthContext", () => ({
	useAuth: vi.fn(),
}));
vi.mock("./MetricChart", () => ({
	MetricChart: () => <div>Enregistreur (mock)</div>,
}));

import { useAuth } from "../auth/AuthContext";
import { useSupervision } from "../supervision/SupervisionContext";
import { EquipementsPage } from "./EquipementsPage";

const routeur: Equipement = {
	id: "eq-1",
	nom: "Routeur cœur",
	adresseIp: "10.0.0.1",
	type: "ROUTEUR",
	localisation: "Salle A",
	etat: "ACTIF",
	description: null,
	derniereMesure: null,
};

describe("EquipementsPage", () => {
	beforeEach(() => {
		vi.mocked(useSupervision).mockReturnValue({
			equipements: [routeur],
			alertes: [],
			chargement: false,
			erreur: null,
			derniereLecture: null,
			tempsReel: false,
			rafraichir: vi.fn(),
			remplacerAlerte: vi.fn(),
		});
		vi.mocked(useAuth).mockReturnValue({
			user: { id: 1, username: "bob", email: "bob@ept.sn", role: "TECHNICIEN" },
			isAuthenticated: true,
			login: vi.fn(),
			logout: vi.fn(),
		});
	});

	it("affiche le parc et ouvre la fiche au clic", () => {
		render(
			<MemoryRouter>
				<EquipementsPage />
			</MemoryRouter>,
		);

		expect(screen.getByText("Routeur cœur")).toBeInTheDocument();
		expect(screen.queryByRole("heading", { name: "Routeur cœur" })).not.toBeInTheDocument();

		fireEvent.click(screen.getByRole("button", { name: /Routeur cœur/ }));

		expect(screen.getByRole("heading", { name: "Routeur cœur" })).toBeInTheDocument();
		expect(screen.getByRole("link", { name: "Modifier" })).toHaveAttribute("href", "/equipements/eq-1/modifier");
	});
});
