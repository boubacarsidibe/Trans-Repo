package com.bouba.backend_trans.rapport.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contenu d'un rapport, tel que la règle F8 l'exige : « le taux de
 * disponibilité, le nombre d'alertes déclenchées, le nombre d'alertes résolues
 * et les équipements les plus sollicités ».
 */
public record SyntheseRapport(
		BigDecimal tauxDisponibilite,
		long alertesDeclenchees,
		long alertesResolues,
		int equipementsSupervises,
		List<EquipementSollicite> equipementsLesPlusSollicites) {

	public record EquipementSollicite(String nom, long alertes) {
	}
}
