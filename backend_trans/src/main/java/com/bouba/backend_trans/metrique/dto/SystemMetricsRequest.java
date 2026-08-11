package com.bouba.backend_trans.metrique.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public class SystemMetricsRequest {

	@NotNull
	@JsonProperty("equipment_id")
	private UUID equipmentId;

	@JsonProperty("cpu_percent")
	private BigDecimal cpuPercent;

	@JsonProperty("memory_percent")
	private BigDecimal memoryPercent;

	@JsonProperty("disk_percent")
	private BigDecimal diskPercent;

	public UUID getEquipmentId() {
		return equipmentId;
	}

	public void setEquipmentId(UUID equipmentId) {
		this.equipmentId = equipmentId;
	}

	public BigDecimal getCpuPercent() {
		return cpuPercent;
	}

	public void setCpuPercent(BigDecimal cpuPercent) {
		this.cpuPercent = cpuPercent;
	}

	public BigDecimal getMemoryPercent() {
		return memoryPercent;
	}

	public void setMemoryPercent(BigDecimal memoryPercent) {
		this.memoryPercent = memoryPercent;
	}

	public BigDecimal getDiskPercent() {
		return diskPercent;
	}

	public void setDiskPercent(BigDecimal diskPercent) {
		this.diskPercent = diskPercent;
	}
}
