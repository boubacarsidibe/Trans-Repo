package com.bouba.backend_trans.metrique.dto;

import java.util.List;
import java.util.UUID;

/**
 * Charge utile d'un événement {@code metric_update} (§8.3).
 *
 * <p>Un agent système remonte une trentaine de métriques par cycle. Elles sont
 * diffusées en un seul événement par équipement plutôt qu'un événement par
 * métrique : même information, une trentaine de messages en moins par cycle et
 * par équipement, et une seule mise à jour d'écran côté console au lieu d'une
 * cascade de rendus.
 */
public record MetriquesEquipement(UUID equipementId, String equipementNom, List<MetriqueResponse> metriques) {
}
