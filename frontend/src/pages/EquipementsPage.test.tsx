import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EquipementsPage } from "./EquipementsPage";
import type { Alerte, Equipement } from "../types/api";

const mockUseSupervision = vi.fn();

vi.mock("../supervision/SupervisionContext", () => ({
	useSupervision: () => mockUseSupervision(),
}));

const mockUseAuth = vi.fn();

vi.mock("../auth/AuthContext", () => ({
	useAuth: () => mockUseAuth(),
}));

// Le parc rendu par la fiche (MetricChart, useSeuils) appelle ces fonctions
// dès qu'un poste est sélectionné : neutralisées pour garder le test centré
// sur la liste, pas sur l'enregistreur.
vi.mock("../api/endpoints", () => ({
	archiveEquipement: vi.fn(),
	fetchEquipementMetriques: vi.fn().mockResolvedValue([]),
	fetchSeuils: vi.fn().mockResolvedValue([]),
}));

function equipement(partiel: Partial<Equipement>): Equipement {
	return {
		id: "eq-1",
		nom: "srv-moodle",
		adresseIp: "10.0.1.10",
		type: "SERVEUR",
		localisation: "Salle serveurs",
		etat: "ACTIF",
		description: null,
		derniereMesure: new Date().toISOString(),
		snmpCommunity: null,
		snmpPort: null,
		interfaceIndex: null,
		...partiel,
	};
}

function rendre(equipements: Equipement[], alertes: Alerte[] = [], role = "TECHNICIEN") {
	mockUseSupervision.mockReturnValue({
		equipements,
		alertes,
		chargement: false,
		erreur: null,
		rafraichir: vi.fn(),
	});
	mockUseAuth.mockReturnValue({ user: role ? { role } : null });

	return render(
		<MemoryRouter>
			<EquipementsPage />
		</MemoryRouter>,
	);
}

describe("EquipementsPage", () => {
	beforeEach(() => {
		mockUseSupervision.mockReset();
		mockUseAuth.mockReset();
	});

	it("affiche le parc et le compteur d'équipements visibles", () => {
		rendre([equipement({ id: "eq-1", nom: "srv-moodle" }), equipement({ id: "eq-2", nom: "sw-core-01" })]);

		expect(screen.getByText("srv-moodle")).toBeInTheDocument();
		expect(screen.getByText("sw-core-01")).toBeInTheDocument();
		expect(screen.getByText("2 sur 2")).toBeInTheDocument();
	});

	it("filtre la liste par nom, IP ou emplacement", async () => {
		rendre([
			equipement({ id: "eq-1", nom: "srv-moodle", adresseIp: "10.0.1.10", localisation: "Salle A" }),
			equipement({ id: "eq-2", nom: "sw-core-01", adresseIp: "10.0.2.20", localisation: "Salle B" }),
		]);

		await userEvent.type(screen.getByLabelText("Filtrer le parc"), "sw-core");

		expect(screen.queryByText("srv-moodle")).not.toBeInTheDocument();
		expect(screen.getByText("sw-core-01")).toBeInTheDocument();
		expect(screen.getByText("1 sur 2")).toBeInTheDocument();
	});

	it("affiche l'état vide quand aucun équipement n'est déclaré", () => {
		rendre([]);

		expect(screen.getByText("Aucun équipement déclaré")).toBeInTheDocument();
	});

	it("affiche 'Déclarer un équipement' pour un technicien mais pas pour un observateur", () => {
		const { unmount } = rendre([equipement({})], [], "TECHNICIEN");
		expect(screen.getByRole("link", { name: "Déclarer un équipement" })).toBeInTheDocument();
		unmount();

		rendre([equipement({})], [], "OBSERVATEUR");
		expect(screen.queryByRole("link", { name: "Déclarer un équipement" })).not.toBeInTheDocument();
	});

	it("ouvre la fiche d'un équipement au clic sur sa ligne", async () => {
		rendre([equipement({ id: "eq-1", nom: "srv-moodle" })]);

		await userEvent.click(screen.getByRole("button", { name: /srv-moodle/ }));

		expect(screen.getByRole("heading", { name: "srv-moodle" })).toBeInTheDocument();
	});
});
