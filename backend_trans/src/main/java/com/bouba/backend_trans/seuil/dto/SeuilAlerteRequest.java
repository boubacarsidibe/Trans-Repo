package com.bouba.backend_trans.seuil.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.bouba.backend_trans.metrique.entity.TypeMetrique;

import jakarta.validation.constraints.NotNull;

public class SeuilAlerteRequest {

	@NotNull
	private TypeMetrique typeMetrique;

	/** {@code null} vise le défaut global, sinon la surcharge de cet équipement. */
	private UUID equipementId;

	private BigDecimal avertissement;

	private BigDecimal critique;

	private Integer dureeSecondes;

	public TypeMetrique getTypeMetrique() {
		return typeMetrique;
	}

	public void setTypeMetrique(TypeMetrique typeMetrique) {
		this.typeMetrique = typeMetrique;
	}

	public UUID getEquipementId() {
		return equipementId;
	}

	public void setEquipementId(UUID equipementId) {
		this.equipementId = equipementId;
	}

	public BigDecimal getAvertissement() {
		return avertissement;
	}

	public void setAvertissement(BigDecimal avertissement) {
		this.avertissement = avertissement;
	}

	public BigDecimal getCritique() {
		return critique;
	}

	public void setCritique(BigDecimal critique) {
		this.critique = critique;
	}

	public Integer getDureeSecondes() {
		return dureeSecondes;
	}

	public void setDureeSecondes(Integer dureeSecondes) {
		this.dureeSecondes = dureeSecondes;
	}
}
