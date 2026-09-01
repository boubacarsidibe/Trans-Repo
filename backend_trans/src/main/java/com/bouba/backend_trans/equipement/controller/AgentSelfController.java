package com.bouba.backend_trans.equipement.controller;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.equipement.dto.EquipementResponse;
import com.bouba.backend_trans.equipement.service.EquipementService;

/**
 * Auto-configuration des agents : authentifié par sa propre clé API (comme
 * pour l'ingestion de métriques), un agent lit ici sa fiche équipement
 * (adresse IP, paramètres SNMP) au lieu de la dupliquer à la main dans un
 * fichier de configuration local. Seule la clé API reste à transporter
 * manuellement jusqu'à l'agent — tout le reste suit les mises à jour faites
 * dans l'interface.
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentSelfController {

	private final EquipementService equipementService;

	public AgentSelfController(EquipementService equipementService) {
		this.equipementService = equipementService;
	}

	@GetMapping("/self")
	public EquipementResponse self(Authentication authentication) {
		UUID equipmentId = (UUID) authentication.getPrincipal();
		return EquipementResponse.fromEntity(equipementService.findById(equipmentId));
	}
}
