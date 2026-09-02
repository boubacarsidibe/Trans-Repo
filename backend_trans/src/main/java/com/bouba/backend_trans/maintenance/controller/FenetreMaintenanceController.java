package com.bouba.backend_trans.maintenance.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.repository.AppUserRepository;
import com.bouba.backend_trans.maintenance.dto.FenetreMaintenanceRequest;
import com.bouba.backend_trans.maintenance.dto.FenetreMaintenanceResponse;
import com.bouba.backend_trans.maintenance.service.FenetreMaintenanceService;

import jakarta.validation.Valid;

/**
 * Fenêtres de maintenance / silence programmé sur un équipement (issue #160).
 *
 * <p>Réservé à l'administrateur et au technicien : l'observateur n'a pas plus
 * accès à la planification des interventions qu'à la configuration des
 * seuils (§4.4).
 */
@RestController
@RequestMapping("/api/v1/equipments/{equipementId}/maintenance-windows")
@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN')")
public class FenetreMaintenanceController {

	private final FenetreMaintenanceService fenetreMaintenanceService;
	private final AppUserRepository appUserRepository;

	public FenetreMaintenanceController(
			FenetreMaintenanceService fenetreMaintenanceService,
			AppUserRepository appUserRepository
	) {
		this.fenetreMaintenanceService = fenetreMaintenanceService;
		this.appUserRepository = appUserRepository;
	}

	@GetMapping
	public List<FenetreMaintenanceResponse> list(@PathVariable UUID equipementId) {
		return fenetreMaintenanceService.findByEquipement(equipementId).stream()
				.map(FenetreMaintenanceResponse::fromEntity)
				.collect(Collectors.toList());
	}

	@PostMapping
	public ResponseEntity<FenetreMaintenanceResponse> create(
			@PathVariable UUID equipementId,
			@Valid @RequestBody FenetreMaintenanceRequest request,
			Authentication authentication
	) {
		AppUser utilisateur = currentUser(authentication);
		FenetreMaintenanceResponse response = FenetreMaintenanceResponse.fromEntity(
				fenetreMaintenanceService.create(equipementId, request, utilisateur));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PutMapping("/{id}/cancel")
	public FenetreMaintenanceResponse cancel(@PathVariable UUID equipementId, @PathVariable UUID id) {
		return FenetreMaintenanceResponse.fromEntity(fenetreMaintenanceService.annuler(equipementId, id));
	}

	private AppUser currentUser(Authentication authentication) {
		return appUserRepository.findByEmail(authentication.getName())
				.orElseThrow(() -> new IllegalStateException("Utilisateur authentifié introuvable."));
	}
}
