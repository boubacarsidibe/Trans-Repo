package com.bouba.backend_trans.collecteur.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/** Charge utile de {@code POST /api/v1/collectors/heartbeat}. */
public class CollecteurHeartbeatRequest {

	@NotBlank
	@JsonProperty("collector_id")
	private String collectorId;

	/** Absent ou {@code null} vaut {@code true} : une instance qui prend la
	 * peine d'envoyer un heartbeat le fait parce qu'elle est active. */
	@JsonProperty("actif")
	private Boolean actif;

	public String getCollectorId() {
		return collectorId;
	}

	public void setCollectorId(String collectorId) {
		this.collectorId = collectorId;
	}

	public boolean isActif() {
		return actif == null || actif;
	}

	public void setActif(Boolean actif) {
		this.actif = actif;
	}
}
