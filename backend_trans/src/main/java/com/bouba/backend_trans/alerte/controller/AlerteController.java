package com.bouba.backend_trans.alerte.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.alerte.dto.AlerteResponse;
import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.StatutAlerte;
import com.bouba.backend_trans.alerte.service.AlerteService;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.repository.AppUserRepository;

@RestController
@RequestMapping("/api/v1/alerts")
@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN', 'OBSERVATEUR')")
public class AlerteController {

	private final AlerteService alerteService;
	private final AppUserRepository appUserRepository;

	public AlerteController(AlerteService alerteService, AppUserRepository appUserRepository) {
		this.alerteService = alerteService;
		this.appUserRepository = appUserRepository;
	}

	@GetMapping
	public List<AlerteResponse> list(@RequestParam(required = false) StatutAlerte statut) {
		List<Alerte> alertes = statut == null ? alerteService.findAll() : alerteService.findByStatut(statut);
		return alertes.stream().map(AlerteResponse::fromEntity).collect(Collectors.toList());
	}

	@GetMapping("/{id}")
	public AlerteResponse getById(@PathVariable UUID id) {
		return AlerteResponse.fromEntity(alerteService.findById(id));
	}

	@PutMapping("/{id}/acknowledge")
	@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN')")
	public AlerteResponse acknowledge(@PathVariable UUID id, Authentication authentication) {
		AppUser utilisateur = currentUser(authentication);
		return AlerteResponse.fromEntity(alerteService.acknowledge(id, utilisateur));
	}

	@PutMapping("/{id}/resolve")
	@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN')")
	public AlerteResponse resolve(@PathVariable UUID id) {
		return AlerteResponse.fromEntity(alerteService.resolve(id));
	}

	private AppUser currentUser(Authentication authentication) {
		return appUserRepository.findByEmail(authentication.getName())
				.orElseThrow(() -> new IllegalStateException("Utilisateur authentifié introuvable."));
	}
}
