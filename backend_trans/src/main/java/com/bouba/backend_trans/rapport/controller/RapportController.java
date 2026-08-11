package com.bouba.backend_trans.rapport.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.rapport.dto.RapportGenerateRequest;
import com.bouba.backend_trans.rapport.dto.RapportResponse;
import com.bouba.backend_trans.rapport.entity.Rapport;
import com.bouba.backend_trans.rapport.service.RapportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN', 'OBSERVATEUR')")
public class RapportController {

	private final RapportService rapportService;

	public RapportController(RapportService rapportService) {
		this.rapportService = rapportService;
	}

	@GetMapping
	public List<RapportResponse> list() {
		return rapportService.findAll().stream()
				.map(RapportResponse::fromEntity)
				.collect(Collectors.toList());
	}

	@PostMapping("/generate")
	@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN')")
	public ResponseEntity<RapportResponse> generate(@Valid @RequestBody RapportGenerateRequest request) {
		Rapport rapport = rapportService.generate(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(RapportResponse.fromEntity(rapport));
	}

	@GetMapping("/{id}/download")
	public ResponseEntity<String> download(@PathVariable UUID id) {
		Rapport rapport = rapportService.findById(id);
		if (rapport.getCheminFichierPdf() == null) {
			return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
					.body("La génération du fichier PDF n'est pas encore implémentée pour ce rapport.");
		}
		return ResponseEntity.ok(rapport.getCheminFichierPdf());
	}
}
