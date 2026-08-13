package com.bouba.backend_trans.disponibilite;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Charge utile du canal {@code /ws/status} : « changements d'état de
 * disponibilité des équipements » (§8.2).
 *
 * <p>La disponibilité est une donnée <em>observée</em>, distincte de l'état
 * administratif de l'équipement (actif / inactif / en maintenance) défini au
 * §6.3 : un équipement reste déclaré « actif » alors même qu'il ne répond plus.
 */
public record DisponibiliteEvenement(
		UUID equipementId,
		String nom,
		boolean disponible,
		LocalDateTime derniereMesure) {
}
