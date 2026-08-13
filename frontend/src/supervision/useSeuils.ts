import { useCallback, useEffect, useState } from "react";
import { fetchSeuils } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { SEUILS, peutIntervenir } from "./libelles";
import type { SeuilAlerte, TypeMetrique } from "../types/api";

/** Repères tracés sur l'enregistreur. Un seuil peut n'être réglé qu'à moitié. */
export interface Reperes {
	attention?: number;
	critique?: number;
}

/**
 * Résout les seuils réellement appliqués par le backend, surcharge d'équipement
 * comprise, pour que le tracé ne mente pas sur ce qui déclenche une alerte.
 *
 * <p>L'observateur n'a aucun accès à la configuration des seuils (§4.4) : pour
 * lui, la lecture est simplement omise et les repères retombent sur les défauts
 * documentés au §11.2.
 */
export function useSeuils(): (equipementId: string, typeMetrique: TypeMetrique) => Reperes | undefined {
	const { user } = useAuth();
	const [seuils, setSeuils] = useState<SeuilAlerte[]>([]);

	useEffect(() => {
		if (!peutIntervenir(user?.role)) {
			setSeuils([]);
			return;
		}
		fetchSeuils()
			.then(setSeuils)
			.catch(() => setSeuils([]));
	}, [user?.role]);

	return useCallback(
		(equipementId: string, typeMetrique: TypeMetrique): Reperes | undefined => {
			const retenu =
				seuils.find((s) => s.typeMetrique === typeMetrique && s.equipementId === equipementId) ??
				seuils.find((s) => s.typeMetrique === typeMetrique && s.equipementId === null);

			if (retenu) {
				return {
					attention: retenu.avertissement ?? undefined,
					critique: retenu.critique ?? undefined,
				};
			}

			return SEUILS[typeMetrique];
		},
		[seuils],
	);
}
