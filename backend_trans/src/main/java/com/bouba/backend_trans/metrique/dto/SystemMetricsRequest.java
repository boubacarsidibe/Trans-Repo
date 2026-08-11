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

	@JsonProperty("swap_percent")
	private BigDecimal swapPercent;

	@JsonProperty("process_count")
	private BigDecimal processCount;

	@JsonProperty("listening_ports_count")
	private BigDecimal listeningPortsCount;

	@JsonProperty("disk_read_kbps")
	private BigDecimal diskReadKbps;

	@JsonProperty("disk_write_kbps")
	private BigDecimal diskWriteKbps;

	@JsonProperty("network_in_kbps")
	private BigDecimal networkInKbps;

	@JsonProperty("network_out_kbps")
	private BigDecimal networkOutKbps;

	@JsonProperty("uptime_seconds")
	private BigDecimal uptimeSeconds;

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

	public BigDecimal getSwapPercent() {
		return swapPercent;
	}

	public void setSwapPercent(BigDecimal swapPercent) {
		this.swapPercent = swapPercent;
	}

	public BigDecimal getProcessCount() {
		return processCount;
	}

	public void setProcessCount(BigDecimal processCount) {
		this.processCount = processCount;
	}

	public BigDecimal getListeningPortsCount() {
		return listeningPortsCount;
	}

	public void setListeningPortsCount(BigDecimal listeningPortsCount) {
		this.listeningPortsCount = listeningPortsCount;
	}

	public BigDecimal getDiskReadKbps() {
		return diskReadKbps;
	}

	public void setDiskReadKbps(BigDecimal diskReadKbps) {
		this.diskReadKbps = diskReadKbps;
	}

	public BigDecimal getDiskWriteKbps() {
		return diskWriteKbps;
	}

	public void setDiskWriteKbps(BigDecimal diskWriteKbps) {
		this.diskWriteKbps = diskWriteKbps;
	}

	public BigDecimal getNetworkInKbps() {
		return networkInKbps;
	}

	public void setNetworkInKbps(BigDecimal networkInKbps) {
		this.networkInKbps = networkInKbps;
	}

	public BigDecimal getNetworkOutKbps() {
		return networkOutKbps;
	}

	public void setNetworkOutKbps(BigDecimal networkOutKbps) {
		this.networkOutKbps = networkOutKbps;
	}

	public BigDecimal getUptimeSeconds() {
		return uptimeSeconds;
	}

	public void setUptimeSeconds(BigDecimal uptimeSeconds) {
		this.uptimeSeconds = uptimeSeconds;
	}
}
