package com.bouba.backend_trans.equipement.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversions IPv4 utilisées par le scan de découverte (issue #152) : test de
 * format, énumération d'une plage {@code ipDebut}..{@code ipFin}.
 *
 * <p>Limitée volontairement à l'IPv4 en notation décimale pointée — c'est la
 * seule forme utilisée pour {@code Equipement.adresseIp} ailleurs dans le
 * parc supervisé.
 */
public final class PlageIpUtils {

	private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

	private PlageIpUtils() {
	}

	public static boolean estFormatIpv4Valide(String ip) {
		if (ip == null) {
			return false;
		}
		Matcher matcher = IPV4.matcher(ip.trim());
		if (!matcher.matches()) {
			return false;
		}
		for (int groupe = 1; groupe <= 4; groupe++) {
			int octet = Integer.parseInt(matcher.group(groupe));
			if (octet < 0 || octet > 255) {
				return false;
			}
		}
		return true;
	}

	/** Suppose un format déjà validé par {@link #estFormatIpv4Valide(String)}. */
	public static long versEntierNonSigne(String ip) {
		String[] octets = ip.trim().split("\\.");
		long valeur = 0;
		for (String octet : octets) {
			valeur = (valeur << 8) | Long.parseLong(octet);
		}
		return valeur;
	}

	public static String depuisEntierNonSigne(long valeur) {
		return ((valeur >> 24) & 0xFF) + "." + ((valeur >> 16) & 0xFF) + "." + ((valeur >> 8) & 0xFF) + "."
				+ (valeur & 0xFF);
	}

	/** Suppose {@code ipDebut} et {@code ipFin} déjà validés (format et taille de plage). */
	public static List<String> enumererPlage(String ipDebut, String ipFin) {
		long debut = versEntierNonSigne(ipDebut);
		long fin = versEntierNonSigne(ipFin);
		List<String> adresses = new ArrayList<>();
		for (long valeur = debut; valeur <= fin; valeur++) {
			adresses.add(depuisEntierNonSigne(valeur));
		}
		return adresses;
	}
}
