package com.bouba.backend_trans.alerte.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.alerte.dto.AlerteResponse;
import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.StatutAlerte;
import com.bouba.backend_trans.alerte.service.AlerteService;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.repository.AppUserRepository;

@RestController
@RequestMapping("/api/v1/alerts")
@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN', 'OBSERVATEUR')")
public class AlerteController {

	/** Plafond de sécurité sur la taille de page demandée. */
	private static final int TAILLE_MAXIMALE = 1000;

	private final AlerteService alerteService;
	private final AppUserRepository appUserRepository;

	public AlerteController(AlerteService alerteService, AppUserRepository appUserRepository) {
		this.alerteService = alerteService;
		this.appUserRepository = appUserRepository;
	}

	/** Liste filtrable et paginée, la plus récente d'abord (§7.9, §10.4). */
	@GetMapping
	public List<AlerteResponse> list(
			@RequestParam(required = false) StatutAlerte statut,
			@RequestParam(required = false) Severite severite,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "200") int taille
	) {
		Pageable pagination = PageRequest.of(
				Math.max(page, 0),
				Math.min(Math.max(taille, 1), TAILLE_MAXIMALE),
				Sort.by(Sort.Direction.DESC, "dateDeclenchement"));

		return alerteService.rechercher(statut, severite, pagination).stream()
				.map(AlerteResponse::fromEntity)
				.collect(Collectors.toList());
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

	/**
	 * Suppression réelle (issue #181), réservée à l'administrateur — plus
	 * restrictive que la prise en compte/résolution. Le service refuse (409)
	 * si l'alerte n'est pas au statut RESOLUE.
	 */
	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMINISTRATEUR')")
	public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
		alerteService.supprimer(id);
		return ResponseEntity.noContent().build();
	}

	private AppUser currentUser(Authentication authentication) {
		return appUserRepository.findByEmail(authentication.getName())
				.orElseThrow(() -> new IllegalStateException("Utilisateur authentifié introuvable."));
	}
}
