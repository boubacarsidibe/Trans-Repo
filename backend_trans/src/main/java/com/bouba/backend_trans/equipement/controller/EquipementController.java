package com.bouba.backend_trans.equipement.controller;

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

import com.bouba.backend_trans.equipement.dto.EquipementRequest;
import com.bouba.backend_trans.equipement.dto.EquipementResponse;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.service.EquipementService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/equipments")
@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN', 'OBSERVATEUR')")
public class EquipementController {

	private final EquipementService equipementService;

	public EquipementController(EquipementService equipementService) {
		this.equipementService = equipementService;
	}

	@GetMapping
	public List<EquipementResponse> list() {
		return equipementService.findAll().stream()
				.map(EquipementResponse::fromEntity)
				.collect(Collectors.toList());
	}

	@GetMapping("/{id}")
	public EquipementResponse getById(@PathVariable UUID id) {
		return EquipementResponse.fromEntity(equipementService.findById(id));
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN')")
	public ResponseEntity<EquipementResponse> create(@Valid @RequestBody EquipementRequest request) {
		Equipement created = equipementService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(EquipementResponse.fromEntity(created, true));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN')")
	public EquipementResponse update(@PathVariable UUID id, @Valid @RequestBody EquipementRequest request) {
		return EquipementResponse.fromEntity(equipementService.update(id, request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN')")
	public ResponseEntity<Void> archive(@PathVariable UUID id) {
		equipementService.archive(id);
		return ResponseEntity.noContent().build();
	}
}
