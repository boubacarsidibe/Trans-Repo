import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { FluxEvenementsPage } from "./FluxEvenementsPage";
import type { EvenementSupervision } from "../supervision/canalTempsReel";
import type { Alerte } from "../types/api";

const mockUseSupervision = vi.fn();

vi.mock("../supervision/SupervisionContext", () => ({
	useSupervision: () => mockUseSupervision(),
	LIMITE_FLUX: 50,
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

function rendre(flux: { id: string; evenement: EvenementSupervision }[]) {
	mockUseSupervision.mockReturnValue({ flux, alertes: [], equipements: [] });
	return render(
		<MemoryRouter>
			<FluxEvenementsPage />
		</MemoryRouter>,
	);
}

describe("FluxEvenementsPage", () => {
	beforeEach(() => {
		mockUseSupervision.mockReset();
	});

	it("affiche l'état vide quand aucun événement n'est encore reçu", () => {
		rendre([]);

		expect(screen.getByText("Aucun événement reçu pour l'instant")).toBeInTheDocument();
	});

	it("liste les événements reçus, avec leur type et l'équipement concerné", () => {
		rendre([
			{
				id: "evt-2",
				evenement: {
					type: "alert_created",
					horodatage: new Date().toISOString(),
					payload: alerte({ equipementNom: "srv-moodle" }),
				},
			},
			{
				id: "evt-1",
				evenement: {
					type: "equipment_status_changed",
					horodatage: new Date().toISOString(),
					payload: { equipementId: "eq-2", nom: "sw-core-01", disponible: false, derniereMesure: null },
				},
			},
		]);

		expect(screen.getByText("Alerte déclenchée")).toBeInTheDocument();
		expect(screen.getByText("Disponibilité")).toBeInTheDocument();
		expect(screen.getByRole("link", { name: "srv-moodle" })).toHaveAttribute("href", "/equipements?poste=eq-1");
		expect(screen.getByRole("link", { name: "sw-core-01" })).toHaveAttribute("href", "/equipements?poste=eq-2");
		expect(screen.getByText("Indisponible")).toBeInTheDocument();
		expect(screen.getByText("2 sur 50 conservés")).toBeInTheDocument();
	});

	it("résume une métrique remontée", () => {
		rendre([
			{
				id: "evt-1",
				evenement: {
					type: "metric_update",
					horodatage: new Date().toISOString(),
					payload: {
						equipementId: "eq-1",
						equipementNom: "srv-moodle",
						metriques: [
							{ id: 1, typeMetrique: "CPU", valeur: 42, unite: "%", horodatage: new Date().toISOString() },
						],
					},
				},
			},
		]);

		expect(screen.getByText("1 métrique remontée")).toBeInTheDocument();
	});
});
