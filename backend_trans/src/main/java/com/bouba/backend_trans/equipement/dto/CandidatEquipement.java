package com.bouba.backend_trans.equipement.dto;

/**
 * Un candidat détecté par le scan de découverte (issue #152).
 *
 * <p>Jamais persisté : c'est l'administrateur qui décide, parmi ces
 * candidats, lesquels déclarer via le CRUD existant ({@code POST
 * /api/v1/equipments}) — le scan ne crée jamais d'équipement de lui-même.
 */
public record CandidatEquipement(
		String ipAddress,
		boolean reachable,
		boolean snmpResponsive,
		String sysDescr,
		String sysObjectID,
		boolean dejaDeclare) {
}
