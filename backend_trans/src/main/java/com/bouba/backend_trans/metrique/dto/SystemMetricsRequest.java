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

	@JsonProperty("load_1min")
	private BigDecimal load1min;

	@JsonProperty("memory_total_mb")
	private BigDecimal memoryTotalMb;

	@JsonProperty("memory_used_mb")
	private BigDecimal memoryUsedMb;

	@JsonProperty("disk_total_gb")
	private BigDecimal diskTotalGb;

	@JsonProperty("disk_used_gb")
	private BigDecimal diskUsedGb;

	@JsonProperty("open_files_limit")
	private BigDecimal openFilesLimit;

	@JsonProperty("process_limit")
	private BigDecimal processLimit;

	@JsonProperty("tcp_services_down")
	private BigDecimal tcpServicesDown;

	@JsonProperty("dns_latency_ms")
	private BigDecimal dnsLatencyMs;

	@JsonProperty("log_lines_count")
	private BigDecimal logLinesCount;

	@JsonProperty("log_lines_match_count")
	private BigDecimal logLinesMatchCount;

	@JsonProperty("watched_file_exists")
	private BigDecimal watchedFileExists;

	@JsonProperty("watched_file_size_bytes")
	private BigDecimal watchedFileSizeBytes;

	@JsonProperty("temperature_max_celsius")
	private BigDecimal temperatureMaxCelsius;

	@JsonProperty("fan_speed_rpm")
	private BigDecimal fanSpeedRpm;

	@JsonProperty("modbus_value")
	private BigDecimal modbusValue;

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

	public BigDecimal getLoad1min() {
		return load1min;
	}

	public void setLoad1min(BigDecimal load1min) {
		this.load1min = load1min;
	}

	public BigDecimal getMemoryTotalMb() {
		return memoryTotalMb;
	}

	public void setMemoryTotalMb(BigDecimal memoryTotalMb) {
		this.memoryTotalMb = memoryTotalMb;
	}

	public BigDecimal getMemoryUsedMb() {
		return memoryUsedMb;
	}

	public void setMemoryUsedMb(BigDecimal memoryUsedMb) {
		this.memoryUsedMb = memoryUsedMb;
	}

	public BigDecimal getDiskTotalGb() {
		return diskTotalGb;
	}

	public void setDiskTotalGb(BigDecimal diskTotalGb) {
		this.diskTotalGb = diskTotalGb;
	}

	public BigDecimal getDiskUsedGb() {
		return diskUsedGb;
	}

	public void setDiskUsedGb(BigDecimal diskUsedGb) {
		this.diskUsedGb = diskUsedGb;
	}

	public BigDecimal getOpenFilesLimit() {
		return openFilesLimit;
	}

	public void setOpenFilesLimit(BigDecimal openFilesLimit) {
		this.openFilesLimit = openFilesLimit;
	}

	public BigDecimal getProcessLimit() {
		return processLimit;
	}

	public void setProcessLimit(BigDecimal processLimit) {
		this.processLimit = processLimit;
	}

	public BigDecimal getTcpServicesDown() {
		return tcpServicesDown;
	}

	public void setTcpServicesDown(BigDecimal tcpServicesDown) {
		this.tcpServicesDown = tcpServicesDown;
	}

	public BigDecimal getDnsLatencyMs() {
		return dnsLatencyMs;
	}

	public void setDnsLatencyMs(BigDecimal dnsLatencyMs) {
		this.dnsLatencyMs = dnsLatencyMs;
	}

	public BigDecimal getLogLinesCount() {
		return logLinesCount;
	}

	public void setLogLinesCount(BigDecimal logLinesCount) {
		this.logLinesCount = logLinesCount;
	}

	public BigDecimal getLogLinesMatchCount() {
		return logLinesMatchCount;
	}

	public void setLogLinesMatchCount(BigDecimal logLinesMatchCount) {
		this.logLinesMatchCount = logLinesMatchCount;
	}

	public BigDecimal getWatchedFileExists() {
		return watchedFileExists;
	}

	public void setWatchedFileExists(BigDecimal watchedFileExists) {
		this.watchedFileExists = watchedFileExists;
	}

	public BigDecimal getWatchedFileSizeBytes() {
		return watchedFileSizeBytes;
	}

	public void setWatchedFileSizeBytes(BigDecimal watchedFileSizeBytes) {
		this.watchedFileSizeBytes = watchedFileSizeBytes;
	}

	public BigDecimal getTemperatureMaxCelsius() {
		return temperatureMaxCelsius;
	}

	public void setTemperatureMaxCelsius(BigDecimal temperatureMaxCelsius) {
		this.temperatureMaxCelsius = temperatureMaxCelsius;
	}

	public BigDecimal getFanSpeedRpm() {
		return fanSpeedRpm;
	}

	public void setFanSpeedRpm(BigDecimal fanSpeedRpm) {
		this.fanSpeedRpm = fanSpeedRpm;
	}

	public BigDecimal getModbusValue() {
		return modbusValue;
	}

	public void setModbusValue(BigDecimal modbusValue) {
		this.modbusValue = modbusValue;
	}
}
