package com.bouba.backend_trans.metrique.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.metrique.dto.MetriqueResponse;
import com.bouba.backend_trans.metrique.dto.NetworkMetricsRequest;
import com.bouba.backend_trans.metrique.dto.SystemMetricsRequest;
import com.bouba.backend_trans.metrique.service.MetriqueService;

import jakarta.validation.Valid;

@RestController
public class MetriqueController {

	private final MetriqueService metriqueService;

	public MetriqueController(MetriqueService metriqueService) {
		this.metriqueService = metriqueService;
	}

	@PostMapping("/api/v1/metrics/system")
	public ResponseEntity<Void> ingestSystem(@Valid @RequestBody SystemMetricsRequest request, Authentication authentication) {
		verifyAgentOwnsEquipment(authentication, request.getEquipmentId());
		metriqueService.ingestSystemMetrics(request);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@PostMapping("/api/v1/metrics/network")
	public ResponseEntity<Void> ingestNetwork(@Valid @RequestBody NetworkMetricsRequest request, Authentication authentication) {
		verifyAgentOwnsEquipment(authentication, request.getEquipmentId());
		metriqueService.ingestNetworkMetrics(request);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	private void verifyAgentOwnsEquipment(Authentication authentication, UUID equipmentId) {
		if (authentication == null
				|| !(authentication.getPrincipal() instanceof UUID agentEquipmentId)
				|| !agentEquipmentId.equals(equipmentId)) {
			throw new AccessDeniedException("La clé API utilisée ne correspond pas à cet équipement.");
		}
	}

	@GetMapping("/api/v1/equipments/{id}/metrics")
	@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN', 'OBSERVATEUR')")
	public List<MetriqueResponse> historique(@PathVariable UUID id) {
		return metriqueService.historiqueParEquipement(id).stream()
				.map(MetriqueResponse::fromEntity)
				.collect(Collectors.toList());
	}
}
