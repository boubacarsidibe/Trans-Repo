package com.bouba.backend_trans.equipement.scan;

import java.io.IOException;
import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Test d'accessibilité par écho ICMP (ou repli TCP selon la JVM) via
 * {@link InetAddress#isReachable(int)} — suffisant pour ce MVP, sans
 * implémentation ICMP bas niveau dédiée.
 */
@Component
public class IcmpHoteAccessible implements HoteAccessible {

	private static final Logger log = LoggerFactory.getLogger(IcmpHoteAccessible.class);

	@Override
	public boolean estAccessible(String adresseIp, int timeoutMs) {
		try {
			return InetAddress.getByName(adresseIp).isReachable(timeoutMs);
		} catch (IOException e) {
			log.debug("Test d'accessibilité de {} en échec : {}", adresseIp, e.getMessage());
			return false;
		}
	}
}
