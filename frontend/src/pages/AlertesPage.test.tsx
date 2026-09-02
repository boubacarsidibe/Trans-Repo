import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AlertesPage } from "./AlertesPage";
import type { Alerte } from "../types/api";

const mockUseSupervision = vi.fn();
const mockRemplacerAlerte = vi.fn();
const mockRafraichir = vi.fn();

vi.mock("../supervision/SupervisionContext", () => ({
	useSupervision: () => mockUseSupervision(),
}));

const mockUseAuth = vi.fn();

vi.mock("../auth/AuthContext", () => ({
	useAuth: () => mockUseAuth(),
}));

const mockPrendreEnCompteAlerte = vi.fn();
const mockResoudreAlerte = vi.fn();
const mockSupprimerAlerte = vi.fn();

vi.mock("../api/endpoints", () => ({
	prendreEnCompteAlerte: (id: string) => mockPrendreEnCompteAlerte(id),
	resoudreAlerte: (id: string) => mockResoudreAlerte(id),
	supprimerAlerte: (id: string) => mockSupprimerAlerte(id),
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
		rafraichir: mockRafraichir,
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
		mockRafraichir.mockReset();
		mockPrendreEnCompteAlerte.mockReset();
		mockResoudreAlerte.mockReset();
		mockSupprimerAlerte.mockReset();
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

	it("ouvre la fiche d'une alerte au clic sur sa ligne", async () => {
		rendre([alerte({ id: "al-1", equipementNom: "srv-moodle" })]);

		await userEvent.click(screen.getByRole("button", { name: /srv-moodle/ }));

		expect(screen.getByRole("heading", { name: /srv-moodle/ })).toBeInTheDocument();
		expect(screen.getByRole("link", { name: "srv-moodle" })).toHaveAttribute(
			"href",
			"/equipements?poste=eq-1",
		);
	});

	it("prend en compte une alerte déclenchée depuis sa fiche, pour un technicien", async () => {
		const miseAJour = alerte({ id: "al-1", statut: "PRISE_EN_COMPTE" });
		mockPrendreEnCompteAlerte.mockResolvedValueOnce(miseAJour);
		rendre([alerte({ id: "al-1", statut: "DECLENCHEE" })]);

		await userEvent.click(screen.getByRole("button", { name: /srv-moodle/ }));
		await userEvent.click(screen.getByRole("button", { name: "Prendre en compte" }));

		expect(mockPrendreEnCompteAlerte).toHaveBeenCalledWith("al-1");
		expect(mockRemplacerAlerte).toHaveBeenCalledWith(miseAJour);
	});

	it("masque les actions de la fiche pour un observateur", async () => {
		rendre([alerte({ id: "al-1", statut: "DECLENCHEE" })], "OBSERVATEUR");

		await userEvent.click(screen.getByRole("button", { name: /srv-moodle/ }));

		expect(screen.queryByRole("button", { name: "Prendre en compte" })).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "Résoudre" })).not.toBeInTheDocument();
	});

	it("referme la fiche au second clic sur la même ligne", async () => {
		rendre([alerte({ id: "al-1", equipementNom: "srv-moodle" })]);

		const ligne = screen.getByRole("button", { name: /srv-moodle/ });
		await userEvent.click(ligne);
		expect(screen.getByRole("heading", { name: /srv-moodle/ })).toBeInTheDocument();

		await userEvent.click(ligne);
		expect(screen.queryByRole("heading", { name: /srv-moodle/ })).not.toBeInTheDocument();
	});

	it("affiche le bouton de suppression pour un administrateur seulement sur une alerte résolue", async () => {
		const { unmount } = rendre(
			[alerte({ id: "al-1", statut: "RESOLUE", dateResolution: new Date().toISOString() })],
			"ADMINISTRATEUR",
		);
		await userEvent.click(screen.getByRole("button", { name: /srv-moodle/ }));
		expect(screen.getByRole("button", { name: "Supprimer" })).toBeInTheDocument();
		unmount();

		rendre([alerte({ id: "al-1", statut: "DECLENCHEE" })], "ADMINISTRATEUR");
		await userEvent.click(screen.getByRole("button", { name: /srv-moodle/ }));
		expect(screen.queryByRole("button", { name: "Supprimer" })).not.toBeInTheDocument();
	});

	it("masque le bouton de suppression d'une alerte résolue pour un technicien", async () => {
		rendre(
			[alerte({ id: "al-1", statut: "RESOLUE", dateResolution: new Date().toISOString() })],
			"TECHNICIEN",
		);

		await userEvent.click(screen.getByRole("button", { name: /srv-moodle/ }));

		expect(screen.queryByRole("button", { name: "Supprimer" })).not.toBeInTheDocument();
	});

	it("supprime l'alerte résolue après confirmation par un second clic", async () => {
		mockSupprimerAlerte.mockResolvedValue(undefined);
		rendre(
			[alerte({ id: "al-1", statut: "RESOLUE", dateResolution: new Date().toISOString() })],
			"ADMINISTRATEUR",
		);
		await userEvent.click(screen.getByRole("button", { name: /srv-moodle/ }));

		const bouton = screen.getByRole("button", { name: "Supprimer" });
		await userEvent.click(bouton);
		expect(screen.getByRole("button", { name: "Confirmer la suppression" })).toBeInTheDocument();
		expect(mockSupprimerAlerte).not.toHaveBeenCalled();

		await userEvent.click(screen.getByRole("button", { name: "Confirmer la suppression" }));
		expect(mockSupprimerAlerte).toHaveBeenCalledWith("al-1");
		expect(mockRafraichir).toHaveBeenCalled();
	});

	it("affiche tel quel le message de refus renvoyé par le backend", async () => {
		mockSupprimerAlerte.mockRejectedValue({
			response: { data: { message: "Seule une alerte résolue peut être supprimée." } },
		});
		rendre(
			[alerte({ id: "al-1", statut: "RESOLUE", dateResolution: new Date().toISOString() })],
			"ADMINISTRATEUR",
		);
		await userEvent.click(screen.getByRole("button", { name: /srv-moodle/ }));

		await userEvent.click(screen.getByRole("button", { name: "Supprimer" }));
		await userEvent.click(screen.getByRole("button", { name: "Confirmer la suppression" }));

		expect(await screen.findByText("Seule une alerte résolue peut être supprimée.")).toBeInTheDocument();
	});
});
