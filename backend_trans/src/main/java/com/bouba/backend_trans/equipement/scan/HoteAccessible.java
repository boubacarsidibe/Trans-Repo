package com.bouba.backend_trans.equipement.scan;

/**
 * Test d'accessibilité réseau d'une IP, préalable au GET SNMP dans le scan de
 * découverte (issue #152). Interface séparée de l'implémentation ICMP pour
 * pouvoir l'injecter par un doublon de test sans dépendance réseau réelle.
 */
public interface HoteAccessible {

	/** {@code true} si l'hôte répond dans le délai imparti, {@code false} sinon (y compris en cas d'erreur). */
	boolean estAccessible(String adresseIp, int timeoutMs);
}
