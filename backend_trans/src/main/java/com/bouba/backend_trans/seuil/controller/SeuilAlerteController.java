package com.bouba.backend_trans.seuil.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.seuil.dto.SeuilAlerteRequest;
import com.bouba.backend_trans.seuil.dto.SeuilAlerteResponse;
import com.bouba.backend_trans.seuil.service.SeuilAlerteService;

import jakarta.validation.Valid;

/**
 * Configuration des seuils d'alerte.
 *
 * <p>Droits calqués sur la matrice des permissions (§4.4) : lecture pour
 * l'administrateur et le technicien, écriture pour le seul administrateur,
 * aucun accès pour l'observateur.
 */
@RestController
@RequestMapping("/api/v1/thresholds")
@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN')")
public class SeuilAlerteController {

	private final SeuilAlerteService seuilAlerteService;

	public SeuilAlerteController(SeuilAlerteService seuilAlerteService) {
		this.seuilAlerteService = seuilAlerteService;
	}

	@GetMapping
	public List<SeuilAlerteResponse> list() {
		return seuilAlerteService.findAll().stream()
				.map(SeuilAlerteResponse::fromEntity)
				.collect(Collectors.toList());
	}

	@GetMapping("/{id}")
	public SeuilAlerteResponse getById(@PathVariable UUID id) {
		return SeuilAlerteResponse.fromEntity(seuilAlerteService.findById(id));
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMINISTRATEUR')")
	public ResponseEntity<SeuilAlerteResponse> create(@Valid @RequestBody SeuilAlerteRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(SeuilAlerteResponse.fromEntity(seuilAlerteService.create(request)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMINISTRATEUR')")
	public SeuilAlerteResponse update(@PathVariable UUID id, @Valid @RequestBody SeuilAlerteRequest request) {
		return SeuilAlerteResponse.fromEntity(seuilAlerteService.update(id, request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMINISTRATEUR')")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		seuilAlerteService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
