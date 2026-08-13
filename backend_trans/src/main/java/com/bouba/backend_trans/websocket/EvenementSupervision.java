package com.bouba.backend_trans.websocket;

import java.time.LocalDateTime;

/**
 * Enveloppe uniforme des messages temps réel (§8.3) : un type d'événement, un
 * horodatage et une charge utile propre à l'événement.
 *
 * <p>La charge utile doit être un objet <em>déjà détaché</em> de la session JPA :
 * l'événement est diffusé après le commit de la transaction, moment où les
 * relations paresseuses ne sont plus chargeables.
 */
public record EvenementSupervision(TypeEvenement type, LocalDateTime horodatage, Object payload) {

	public static EvenementSupervision de(TypeEvenement type, Object payload) {
		return new EvenementSupervision(type, LocalDateTime.now(), payload);
	}
}
