package com.bouba.backend_trans.metrique.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public class NetworkMetricsRequest {

	@NotNull
	@JsonProperty("equipment_id")
	private UUID equipmentId;

	@JsonProperty("bandwidth_mbps")
	private BigDecimal bandwidthMbps;

	@JsonProperty("latency_ms")
	private BigDecimal latencyMs;

	@JsonProperty("error_rate_percent")
	private BigDecimal errorRatePercent;

	public UUID getEquipmentId() {
		return equipmentId;
	}

	public void setEquipmentId(UUID equipmentId) {
		this.equipmentId = equipmentId;
	}

	public BigDecimal getBandwidthMbps() {
		return bandwidthMbps;
	}

	public void setBandwidthMbps(BigDecimal bandwidthMbps) {
		this.bandwidthMbps = bandwidthMbps;
	}

	public BigDecimal getLatencyMs() {
		return latencyMs;
	}

	public void setLatencyMs(BigDecimal latencyMs) {
		this.latencyMs = latencyMs;
	}

	public BigDecimal getErrorRatePercent() {
		return errorRatePercent;
	}

	public void setErrorRatePercent(BigDecimal errorRatePercent) {
		this.errorRatePercent = errorRatePercent;
	}
}
