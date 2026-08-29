import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AlertesPage } from "./AlertesPage";
import type { Alerte } from "../types/api";

const mockUseSupervision = vi.fn();
const mockRemplacerAlerte = vi.fn();

vi.mock("../supervision/SupervisionContext", () => ({
	useSupervision: () => mockUseSupervision(),
}));

const mockUseAuth = vi.fn();

vi.mock("../auth/AuthContext", () => ({
	useAuth: () => mockUseAuth(),
}));

const mockPrendreEnCompteAlerte = vi.fn();
const mockResoudreAlerte = vi.fn();

vi.mock("../api/endpoints", () => ({
	prendreEnCompteAlerte: (id: string) => mockPrendreEnCompteAlerte(id),
	resoudreAlerte: (id: string) => mockResoudreAlerte(id),
}));

function alerte(partiel: Partial<Alerte>): Alerte {
	return {
		id: "al-1",
		equipementId: "eq-1",
		equipementNom: "srv-moodle",
		typeAnomalie: "CPU",
		severite: "CRITIQUE",
		statut: "DECLENCHEE",
		dateDeclenchement: new Date().toISOString(),
		dateResolution: null,
		utilisateurPriseEnCharge: null,
		...partiel,
	};
}

function rendre(alertes: Alerte[], role = "TECHNICIEN") {
	mockUseSupervision.mockReturnValue({
		alertes,
		chargement: false,
		erreur: null,
		remplacerAlerte: mockRemplacerAlerte,
	});
	mockUseAuth.mockReturnValue({ user: role ? { role } : null });

	return render(
		<MemoryRouter>
			<AlertesPage />
		</MemoryRouter>,
	);
}

describe("AlertesPage", () => {
	beforeEach(() => {
		mockUseSupervision.mockReset();
		mockUseAuth.mockReset();
		mockRemplacerAlerte.mockReset();
		mockPrendreEnCompteAlerte.mockReset();
		mockResoudreAlerte.mockReset();
	});

	it("affiche le journal des alertes et le compteur d'entrées", () => {
		rendre([alerte({ id: "al-1" }), alerte({ id: "al-2", equipementNom: "sw-core-01" })]);

		expect(screen.getByText("srv-moodle")).toBeInTheDocument();
		expect(screen.getByText("sw-core-01")).toBeInTheDocument();
		expect(screen.getByText("2 entrées")).toBeInTheDocument();
	});

	it("filtre par statut", async () => {
		rendre([
			alerte({ id: "al-1", statut: "DECLENCHEE", equipementNom: "srv-moodle" }),
			alerte({ id: "al-2", statut: "RESOLUE", equipementNom: "sw-core-01", dateResolution: new Date().toISOString() }),
		]);

		await userEvent.click(screen.getByRole("button", { name: "Résolues" }));

		expect(screen.queryByText("srv-moodle")).not.toBeInTheDocument();
		expect(screen.getByText("sw-core-01")).toBeInTheDocument();
		expect(screen.getByText("1 entrées")).toBeInTheDocument();
	});

	it("affiche l'état vide quand le journal est vide", () => {
		rendre([]);

		expect(screen.getByText("Aucune alerte")).toBeInTheDocument();
	});

	it("prend en compte une alerte déclenchée au clic, pour un technicien", async () => {
		const miseAJour = alerte({ id: "al-1", statut: "PRISE_EN_COMPTE" });
		mockPrendreEnCompteAlerte.mockResolvedValueOnce(miseAJour);
		rendre([alerte({ id: "al-1", statut: "DECLENCHEE" })]);

		await userEvent.click(screen.getByRole("button", { name: "Prendre en compte" }));

		expect(mockPrendreEnCompteAlerte).toHaveBeenCalledWith("al-1");
		expect(mockRemplacerAlerte).toHaveBeenCalledWith(miseAJour);
	});

	it("masque les actions pour un observateur", () => {
		rendre([alerte({ id: "al-1", statut: "DECLENCHEE" })], "OBSERVATEUR");

		expect(screen.queryByRole("button", { name: "Prendre en compte" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Résoudre" })).not.toBeInTheDocument();
	});
});
