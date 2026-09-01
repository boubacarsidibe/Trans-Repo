package com.bouba.backend_trans.collecteur;

import java.time.LocalDateTime;

/**
 * Charge utile du canal {@code /ws/status} pour l'arrêt/la reprise du
 * collecteur réseau actif (issue #157) — le pendant de
 * {@code DisponibiliteEvenement} pour les équipements (F3).
 */
public record CollecteurEvenement(
		String collecteurId,
		boolean disponible,
		LocalDateTime dernierHeartbeat) {
}
