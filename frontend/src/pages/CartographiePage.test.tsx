import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CartographiePage } from "./CartographiePage";
import type { Equipement } from "../types/api";

const mockUseSupervision = vi.fn();

vi.mock("../supervision/SupervisionContext", () => ({
	useSupervision: () => mockUseSupervision(),
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
		dependDeId: null,
		dependDeNom: null,
		...partiel,
	};
}

function rendre(equipements: Equipement[], chargement = false, erreur: string | null = null) {
	mockUseSupervision.mockReturnValue({ equipements, alertes: [], chargement, erreur });
	return render(
		<MemoryRouter>
			<CartographiePage />
		</MemoryRouter>,
	);
}

describe("CartographiePage", () => {
	beforeEach(() => {
		mockUseSupervision.mockReset();
	});

	it("affiche l'état vide quand le parc est vide", () => {
		rendre([]);

		expect(screen.getByText("Parc vide")).toBeInTheDocument();
	});

	it("place chaque équipement comme un nœud nommé de la carte", () => {
		rendre([
			equipement({ id: "sw1", nom: "sw-core-01" }),
			equipement({ id: "ap1", nom: "ap-git-r1", dependDeId: "sw1", dependDeNom: "sw-core-01" }),
		]);

		expect(screen.getByText("sw-core-01")).toBeInTheDocument();
		expect(screen.getByText("ap-git-r1")).toBeInTheDocument();
		expect(screen.getByText("2 équipements · 1 lien de dépendance")).toBeInTheDocument();
	});

	it("signale l'absence de dépendances déclarées quand le parc est plat", () => {
		rendre([equipement({ id: "e1" }), equipement({ id: "e2", nom: "e2" })]);

		expect(
			screen.getByText(/Aucune dépendance déclarée pour l'instant/),
		).toBeInTheDocument();
	});
});
