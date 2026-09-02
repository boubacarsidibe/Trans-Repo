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

import com.bouba.backend_trans.equipement.dto.CandidatEquipement;
import com.bouba.backend_trans.equipement.dto.EquipementRequest;
import com.bouba.backend_trans.equipement.dto.EquipementResponse;
import com.bouba.backend_trans.equipement.dto.ScanRequest;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.service.EquipementScanService;
import com.bouba.backend_trans.equipement.service.EquipementService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/equipments")
@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN', 'OBSERVATEUR')")
public class EquipementController {

	private final EquipementService equipementService;
	private final EquipementScanService equipementScanService;

	public EquipementController(EquipementService equipementService, EquipementScanService equipementScanService) {
		this.equipementService = equipementService;
		this.equipementScanService = equipementScanService;
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

	/**
	 * Suppression définitive (issue #177), réservée à l'administrateur — plus
	 * restrictive que l'archivage puisqu'elle efface la ligne pour de bon. Le
	 * service refuse (409) si l'équipement conserve la moindre trace.
	 */
	@DeleteMapping("/{id}/definitif")
	@PreAuthorize("hasRole('ADMINISTRATEUR')")
	public ResponseEntity<Void> supprimerDefinitivement(@PathVariable UUID id) {
		equipementService.supprimerDefinitivement(id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Scan de découverte (issue #152) sur une plage d'IP : ICMP puis GET SNMP
	 * {@code sysDescr}/{@code sysObjectID}. Ne crée rien — l'administrateur
	 * déclare ensuite manuellement les candidats retenus via {@link #create}.
	 */
	@PostMapping("/scan")
	@PreAuthorize("hasAnyRole('ADMINISTRATEUR', 'TECHNICIEN')")
	public List<CandidatEquipement> scan(@Valid @RequestBody ScanRequest request) {
		return equipementScanService.scanner(request);
	}
}
