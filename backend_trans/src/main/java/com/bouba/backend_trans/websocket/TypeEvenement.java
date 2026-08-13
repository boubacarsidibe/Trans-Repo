package com.bouba.backend_trans.websocket;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Types d'événements diffusés sur les canaux temps réel (§8.3). Chaque type
 * connaît son canal : c'est ce qui évite qu'un émetteur se trompe de
 * destination.
 */
public enum TypeEvenement {

	METRIC_UPDATE("metric_update", CanalSupervision.METRICS),
	ALERT_CREATED("alert_created", CanalSupervision.ALERTS),
	ALERT_UPDATED("alert_updated", CanalSupervision.ALERTS),
	ALERT_ACKNOWLEDGED("alert_acknowledged", CanalSupervision.ALERTS),
	ALERT_RESOLVED("alert_resolved", CanalSupervision.ALERTS),
	EQUIPMENT_STATUS_CHANGED("equipment_status_changed", CanalSupervision.STATUS);

	private final String code;
	private final CanalSupervision canal;

	TypeEvenement(String code, CanalSupervision canal) {
		this.code = code;
		this.canal = canal;
	}

	/** Le contrat §8.3 nomme les événements en minuscules avec des tirets bas. */
	@JsonValue
	public String getCode() {
		return code;
	}

	public CanalSupervision getCanal() {
		return canal;
	}
}
