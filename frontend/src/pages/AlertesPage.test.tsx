import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Alerte } from "../types/api";

vi.mock("../supervision/SupervisionContext", () => ({
	useSupervision: vi.fn(),
}));
vi.mock("../auth/AuthContext", () => ({
	useAuth: vi.fn(),
}));
vi.mock("../api/endpoints", () => ({
	prendreEnCompteAlerte: vi.fn(),
	resoudreAlerte: vi.fn(),
}));

import { prendreEnCompteAlerte, resoudreAlerte } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { useSupervision } from "../supervision/SupervisionContext";
import { AlertesPage } from "./AlertesPage";

const alerteDeclenchee: Alerte = {
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

const alerteResolue: Alerte = {
	id: "al-2",
	equipementId: "eq-2",
	equipementNom: "Serveur applicatif",
	typeAnomalie: "CPU",
	severite: "AVERTISSEMENT",
	statut: "RESOLUE",
	dateDeclenchement: "2026-08-29T07:00:00Z",
	dateResolution: "2026-08-29T07:30:00Z",
	utilisateurPriseEnCharge: "bob",
};

function rendre() {
	return render(
		<MemoryRouter>
			<AlertesPage />
		</MemoryRouter>,
	);
}

describe("AlertesPage", () => {
	const remplacerAlerte = vi.fn();

	beforeEach(() => {
		vi.clearAllMocks();
		vi.mocked(useSupervision).mockReturnValue({
			equipements: [],
			alertes: [alerteDeclenchee, alerteResolue],
			chargement: false,
			erreur: null,
			derniereLecture: null,
			tempsReel: false,
			rafraichir: vi.fn(),
			remplacerAlerte,
		});
		vi.mocked(useAuth).mockReturnValue({
			user: { id: 1, username: "bob", email: "bob@ept.sn", role: "TECHNICIEN" },
			isAuthenticated: true,
			login: vi.fn(),
			logout: vi.fn(),
		});
	});

	it("affiche les deux alertes et filtre par statut", () => {
		rendre();

		expect(screen.getByText("Routeur cœur")).toBeInTheDocument();
		expect(screen.getByText("Serveur applicatif")).toBeInTheDocument();

		fireEvent.click(screen.getByRole("button", { name: "Résolues" }));

		expect(screen.queryByText("Routeur cœur")).not.toBeInTheDocument();
		expect(screen.getByText("Serveur applicatif")).toBeInTheDocument();
	});

	it("prend en compte une alerte déclenchée", async () => {
		vi.mocked(prendreEnCompteAlerte).mockResolvedValue({ ...alerteDeclenchee, statut: "PRISE_EN_COMPTE" });
		rendre();

		fireEvent.click(screen.getByRole("button", { name: "Prendre en compte" }));

		await waitFor(() => expect(prendreEnCompteAlerte).toHaveBeenCalledWith("al-1"));
		await waitFor(() =>
			expect(remplacerAlerte).toHaveBeenCalledWith(expect.objectContaining({ statut: "PRISE_EN_COMPTE" })),
		);
	});

	it("résout une alerte déclenchée", async () => {
		vi.mocked(resoudreAlerte).mockResolvedValue({ ...alerteDeclenchee, statut: "RESOLUE" });
		rendre();

		fireEvent.click(screen.getByRole("button", { name: "Résoudre" }));

		await waitFor(() => expect(resoudreAlerte).toHaveBeenCalledWith("al-1"));
		await waitFor(() =>
			expect(remplacerAlerte).toHaveBeenCalledWith(expect.objectContaining({ statut: "RESOLUE" })),
		);
	});
});
