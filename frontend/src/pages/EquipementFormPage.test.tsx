import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Equipement } from "../types/api";

vi.mock("../supervision/SupervisionContext", () => ({
	useSupervision: vi.fn(),
}));
vi.mock("../api/endpoints", () => ({
	fetchEquipement: vi.fn(),
	createEquipement: vi.fn(),
	updateEquipement: vi.fn(),
}));

import { createEquipement, fetchEquipement, updateEquipement } from "../api/endpoints";
import { useSupervision } from "../supervision/SupervisionContext";
import { EquipementFormPage } from "./EquipementFormPage";

const routeurExistant: Equipement = {
	id: "eq-1",
	nom: "Routeur cœur",
	adresseIp: "10.0.0.1",
	type: "ROUTEUR",
	localisation: "Salle A",
	etat: "ACTIF",
	description: null,
	derniereMesure: null,
};

function rendreAvecRoutes(entree: string) {
	return render(
		<MemoryRouter initialEntries={[entree]}>
			<Routes>
				<Route path="/equipements" element={<p>Liste des équipements</p>} />
				<Route path="/equipements/nouveau" element={<EquipementFormPage />} />
				<Route path="/equipements/:id/modifier" element={<EquipementFormPage />} />
			</Routes>
		</MemoryRouter>,
	);
}

describe("EquipementFormPage", () => {
	const rafraichir = vi.fn();

	beforeEach(() => {
		vi.clearAllMocks();
		vi.mocked(useSupervision).mockReturnValue({
			equipements: [],
			alertes: [],
			chargement: false,
			erreur: null,
			derniereLecture: null,
			tempsReel: false,
			rafraichir,
			remplacerAlerte: vi.fn(),
		});
	});

	it("déclare un nouvel équipement", async () => {
		const cree: Equipement = { ...routeurExistant, id: "eq-9", nom: "Nouveau routeur", cleApi: "cle-generee" };
		vi.mocked(createEquipement).mockResolvedValue(cree);

		rendreAvecRoutes("/equipements/nouveau");

		fireEvent.change(screen.getByLabelText("Nom"), { target: { value: "Nouveau routeur" } });
		fireEvent.change(screen.getByLabelText("Adresse IP"), { target: { value: "10.0.0.9" } });
		fireEvent.click(screen.getByRole("button", { name: "Déclarer l'équipement" }));

		await waitFor(() =>
			expect(createEquipement).toHaveBeenCalledWith(
				expect.objectContaining({ nom: "Nouveau routeur", adresseIp: "10.0.0.9" }),
			),
		);
		expect(await screen.findByText("Nouveau routeur est déclaré")).toBeInTheDocument();
		expect(screen.getByText("cle-generee")).toBeInTheDocument();
		expect(rafraichir).toHaveBeenCalled();
	});

	it("modifie un équipement existant", async () => {
		vi.mocked(fetchEquipement).mockResolvedValue(routeurExistant);
		vi.mocked(updateEquipement).mockResolvedValue({ ...routeurExistant, nom: "Routeur renommé" });

		rendreAvecRoutes("/equipements/eq-1/modifier");

		expect(await screen.findByDisplayValue("Routeur cœur")).toBeInTheDocument();

		fireEvent.change(screen.getByLabelText("Nom"), { target: { value: "Routeur renommé" } });
		fireEvent.click(screen.getByRole("button", { name: "Enregistrer les modifications" }));

		await waitFor(() =>
			expect(updateEquipement).toHaveBeenCalledWith(
				"eq-1",
				expect.objectContaining({ nom: "Routeur renommé", adresseIp: "10.0.0.1" }),
			),
		);
		expect(await screen.findByText("Liste des équipements")).toBeInTheDocument();
		expect(rafraichir).toHaveBeenCalled();
	});
});
