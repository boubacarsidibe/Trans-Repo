package com.bouba.backend_trans.collecteur.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.collecteur.dto.CollecteurHeartbeatRequest;
import com.bouba.backend_trans.collecteur.service.CollecteurService;

import jakarta.validation.Valid;

@RestController
public class CollecteurController {

	private final CollecteurService collecteurService;

	public CollecteurController(CollecteurService collecteurService) {
		this.collecteurService = collecteurService;
	}

	/**
	 * Heartbeat périodique d'une instance du collecteur réseau (issue #157).
	 * Authentifié par la clé partagée {@code app.collecteurs.cle-api}
	 * (en-tête {@code X-Collector-Key}, cf. {@code CollecteurApiKeyAuthenticationFilter}) :
	 * contrairement aux équipements, les instances du collecteur ne sont pas
	 * provisionnées à l'avance, la ligne est créée au premier appel.
	 */
	@PostMapping("/api/v1/collectors/heartbeat")
	public ResponseEntity<Void> heartbeat(@Valid @RequestBody CollecteurHeartbeatRequest request) {
		collecteurService.enregistrerHeartbeat(request.getCollectorId(), request.isActif());
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}
}
