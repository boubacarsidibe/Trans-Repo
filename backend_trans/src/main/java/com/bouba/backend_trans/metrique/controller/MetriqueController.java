package com.bouba.backend_trans.metrique.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.metrique.dto.MetriqueResponse;
import com.bouba.backend_trans.metrique.dto.NetworkMetricsRequest;
import com.bouba.backend_trans.metrique.dto.SystemMetricsRequest;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;
import com.bouba.backend_trans.metrique.service.MetriqueService;

import jakarta.validation.Valid;

@RestController
public class MetriqueController {

	/** Fenêtre tracée par le tableau de bord en l'absence de borne (§10.2). */
	private static final int FENETRE_PAR_DEFAUT_HEURES = 24;

	/** Plafond de sécurité : personne ne réclame 90 jours en une requête. */
	private static final int TAILLE_MAXIMALE = 5000;

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

	/**
	 * Historique des métriques d'un équipement, borné et paginé (§7.9).
	 *
	 * <p>Sans bornes explicites, la réponse couvre les dernières 24 heures. La
	 * taille demandée est plafonnée : avec 90 jours de rétention, un équipement
	 * cumule plusieurs millions de mesures.
	 */
	@GetMapping("/api/v1/equipments/{id}/metrics")
	@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN', 'OBSERVATEUR')")
	public List<MetriqueResponse> historique(
			@PathVariable UUID id,
			@RequestParam(required = false) TypeMetrique type,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime depuis,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "1000") int taille
	) {
		LocalDateTime debut = depuis != null ? depuis : LocalDateTime.now().minusHours(FENETRE_PAR_DEFAUT_HEURES);
		Pageable pagination = PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), TAILLE_MAXIMALE));

		return metriqueService.historiqueParEquipement(id, type, debut, pagination).stream()
				.map(MetriqueResponse::fromEntity)
				.collect(Collectors.toList());
	}

	private void verifyAgentOwnsEquipment(Authentication authentication, UUID equipmentId) {
		if (authentication == null
				|| !(authentication.getPrincipal() instanceof UUID agentEquipmentId)
				|| !agentEquipmentId.equals(equipmentId)) {
			throw new AccessDeniedException("La clé API utilisée ne correspond pas à cet équipement.");
		}
	}
}
