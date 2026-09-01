package com.bouba.backend_trans.equipement.scan;

/** Résultat d'un GET SNMP {@code sysDescr}/{@code sysObjectID} sur une IP (issue #152). */
public record SnmpResultat(boolean responsive, String sysDescr, String sysObjectID) {

	public static final SnmpResultat AUCUNE_REPONSE = new SnmpResultat(false, null, null);
}
