package com.bouba.backend_trans.equipement.scan;

/**
 * Client SNMP utilisé par le scan de découverte (issue #152). Interface
 * séparée de l'implémentation SNMP4J pour pouvoir l'injecter par un doublon
 * de test sans dépendance réseau réelle.
 */
public interface SnmpClient {

	/** Ne lève jamais : toute erreur ou absence de réponse se traduit par {@link SnmpResultat#AUCUNE_REPONSE}. */
	SnmpResultat interroger(String adresseIp, int port, String communaute, int timeoutMs);
}
