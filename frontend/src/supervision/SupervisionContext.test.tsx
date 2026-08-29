import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Alerte, Equipement } from "../types/api";
import type { EvenementSupervision } from "./canalTempsReel";

const canaux: {
	surEvenement?: (evenement: EvenementSupervision) => void;
	surEtat?: (actif: boolean) => void;
} = {};

vi.mock("./canalTempsReel", () => ({
	ouvrirCanaux: vi.fn((surEvenement: (e: EvenementSupervision) => void, surEtat: (actif: boolean) => void) => {
		canaux.surEvenement = surEvenement;
		canaux.surEtat = surEtat;
		return () => {};
	}),
}));

vi.mock("../api/endpoints", () => ({
	fetchEquipements: vi.fn(),
	fetchAlertes: vi.fn(),
}));

import { fetchAlertes, fetchEquipements } from "../api/endpoints";
import { SupervisionProvider, useSupervision } from "./SupervisionContext";

const equipement: Equipement = {
	id: "eq-1",
	nom: "Routeur cœur",
	adresseIp: "10.0.0.1",
	type: "ROUTEUR",
	localisation: "Salle serveur",
	etat: "ACTIF",
	description: null,
	derniereMesure: null,
};

const alerte: Alerte = {
	id: "al-1",
	equipementId: "eq-1",
	equipementNom: "Routeur cœur",
	typeAnomalie: "RESEAU",
	severite: "AVERTISSEMENT",
	statut: "DECLENCHEE",
	dateDeclenchement: "2026-08-29T00:00:00Z",
	dateResolution: null,
	utilisateurPriseEnCharge: null,
};

describe("SupervisionProvider", () => {
	beforeEach(() => {
		vi.mocked(fetchEquipements).mockResolvedValue([equipement]);
		vi.mocked(fetchAlertes).mockResolvedValue([alerte]);
		delete canaux.surEvenement;
		delete canaux.surEtat;
	});

	it("charge le parc et les alertes au montage", async () => {
		const { result } = renderHook(() => useSupervision(), { wrapper: SupervisionProvider });

		await waitFor(() => expect(result.current.chargement).toBe(false));

		expect(result.current.equipements).toEqual([equipement]);
		expect(result.current.alertes).toEqual([alerte]);
		expect(result.current.erreur).toBeNull();
	});

	it("une alerte reçue par le canal temps réel met à jour l'état affiché", async () => {
		const { result } = renderHook(() => useSupervision(), { wrapper: SupervisionProvider });
		await waitFor(() => expect(result.current.chargement).toBe(false));

		const alerteMiseAJour: Alerte = { ...alerte, statut: "PRISE_EN_COMPTE" };
		act(() => {
			canaux.surEvenement?.({
				type: "alert_updated",
				horodatage: "2026-08-29T00:05:00Z",
				payload: alerteMiseAJour,
			});
		});

		expect(result.current.alertes).toEqual([alerteMiseAJour]);
	});

	it("un équipement redevenu silencieux met à jour la dernière mesure affichée", async () => {
		const { result } = renderHook(() => useSupervision(), { wrapper: SupervisionProvider });
		await waitFor(() => expect(result.current.chargement).toBe(false));

		act(() => {
			canaux.surEvenement?.({
				type: "equipment_status_changed",
				horodatage: "2026-08-29T00:05:00Z",
				payload: { equipementId: "eq-1", nom: "Routeur cœur", disponible: false, derniereMesure: "2026-08-29T00:04:00Z" },
			});
		});

		expect(result.current.equipements[0].derniereMesure).toBe("2026-08-29T00:04:00Z");
	});

	it("reflète la reconnexion et la coupure du canal temps réel", async () => {
		const { result } = renderHook(() => useSupervision(), { wrapper: SupervisionProvider });
		await waitFor(() => expect(result.current.chargement).toBe(false));

		expect(result.current.tempsReel).toBe(false);

		act(() => canaux.surEtat?.(true));
		expect(result.current.tempsReel).toBe(true);

		act(() => canaux.surEtat?.(false));
		expect(result.current.tempsReel).toBe(false);
	});
});
