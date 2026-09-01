package com.bouba.backend_trans.equipement.scan;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Interroge un équipement en SNMPv2c (GET {@code sysDescr}/{@code sysObjectID})
 * via SNMP4J, pour le scan de découverte (issue #152).
 *
 * <p>Un seul {@link Snmp} partagé pour toute l'instance : SNMP4J est conçu
 * pour être réutilisé et partagé entre threads ; en ouvrir un par IP scannée
 * multiplierait inutilement les sockets UDP ouverts pendant un scan de plage.
 */
@Component
public class Snmp4jClient implements SnmpClient {

	private static final Logger log = LoggerFactory.getLogger(Snmp4jClient.class);

	private static final OID SYS_DESCR = new OID("1.3.6.1.2.1.1.1.0");
	private static final OID SYS_OBJECT_ID = new OID("1.3.6.1.2.1.1.2.0");

	private final Snmp snmp;

	public Snmp4jClient() throws IOException {
		this.snmp = new Snmp(new DefaultUdpTransportMapping());
		this.snmp.listen();
	}

	@Override
	public SnmpResultat interroger(String adresseIp, int port, String communaute, int timeoutMs) {
		CommunityTarget<UdpAddress> cible = new CommunityTarget<>();
		cible.setCommunity(new OctetString(communaute));
		cible.setAddress(new UdpAddress(adresseIp + "/" + port));
		cible.setRetries(0);
		cible.setTimeout(timeoutMs);
		cible.setVersion(SnmpConstants.version2c);

		PDU pdu = new PDU();
		pdu.add(new VariableBinding(SYS_DESCR));
		pdu.add(new VariableBinding(SYS_OBJECT_ID));
		pdu.setType(PDU.GET);

		try {
			ResponseEvent<UdpAddress> evenement = snmp.send(pdu, cible);
			PDU reponse = evenement == null ? null : evenement.getResponse();
			if (reponse == null || reponse.getErrorStatus() != PDU.noError || reponse.size() < 2) {
				return SnmpResultat.AUCUNE_REPONSE;
			}
			String sysDescr = reponse.get(0).getVariable().toString();
			String sysObjectID = reponse.get(1).getVariable().toString();
			return new SnmpResultat(true, sysDescr, sysObjectID);
		} catch (IOException e) {
			log.debug("Interrogation SNMP de {} en échec : {}", adresseIp, e.getMessage());
			return SnmpResultat.AUCUNE_REPONSE;
		}
	}

	@PreDestroy
	public void fermer() {
		try {
			snmp.close();
		} catch (IOException e) {
			log.debug("Fermeture du client SNMP en échec : {}", e.getMessage());
		}
	}
}
