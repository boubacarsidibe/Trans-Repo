package com.bouba.backend_trans.sante;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.websocket.CanalSupervision;
import com.bouba.backend_trans.websocket.SupervisionWebSocketHandler;

/**
 * Auto-supervision de la plateforme (§16) : « une défaillance du système de
 * monitoring ne doit pas passer elle-même inaperçue ».
 *
 * <p>Volontairement accessible sans authentification, pour qu'une sonde externe
 * puisse l'interroger. Aucune donnée d'infrastructure n'y transite.
 */
@RestController
public class SanteController {

	private static final int DELAI_VALIDATION_SECONDES = 2;

	private final DataSource dataSource;
	private final SupervisionWebSocketHandler webSocketHandler;

	public SanteController(DataSource dataSource, SupervisionWebSocketHandler webSocketHandler) {
		this.dataSource = dataSource;
		this.webSocketHandler = webSocketHandler;
	}

	@GetMapping("/api/v1/health")
	public ResponseEntity<Map<String, Object>> sante() {
		boolean baseJoignable = baseJoignable();

		Map<String, Object> corps = new LinkedHashMap<>();
		corps.put("statut", baseJoignable ? "UP" : "DOWN");
		corps.put("base", baseJoignable ? "UP" : "DOWN");
		corps.put("sessionsTempsReel", Map.of(
				"metrics", webSocketHandler.nombreDeSessions(CanalSupervision.METRICS),
				"alerts", webSocketHandler.nombreDeSessions(CanalSupervision.ALERTS),
				"status", webSocketHandler.nombreDeSessions(CanalSupervision.STATUS)));

		// Un 503 permet à une sonde externe de conclure sans analyser le corps.
		return ResponseEntity.status(baseJoignable ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(corps);
	}

	private boolean baseJoignable() {
		try (Connection connection = dataSource.getConnection()) {
			return connection.isValid(DELAI_VALIDATION_SECONDES);
		} catch (Exception ex) {
			return false;
		}
	}
}
