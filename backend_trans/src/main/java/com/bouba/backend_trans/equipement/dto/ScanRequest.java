package com.bouba.backend_trans.equipement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Requête de scan de découverte (issue #152) : une plage d'IP de début/fin,
 * une communauté SNMP et un port SNMP.
 */
@ValidPlageIp
public class ScanRequest {

	/** Taille maximale d'une plage scannée en un seul appel — l'équivalent d'un réseau /24. */
	public static final int TAILLE_MAX_PLAGE = 254;

	@NotBlank
	private String ipDebut;

	@NotBlank
	private String ipFin;

	@NotBlank
	private String communaute;

	@NotNull
	@Min(1)
	@Max(65535)
	private Integer port;

	public String getIpDebut() {
		return ipDebut;
	}

	public void setIpDebut(String ipDebut) {
		this.ipDebut = ipDebut;
	}

	public String getIpFin() {
		return ipFin;
	}

	public void setIpFin(String ipFin) {
		this.ipFin = ipFin;
	}

	public String getCommunaute() {
		return communaute;
	}

	public void setCommunaute(String communaute) {
		this.communaute = communaute;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}
}
