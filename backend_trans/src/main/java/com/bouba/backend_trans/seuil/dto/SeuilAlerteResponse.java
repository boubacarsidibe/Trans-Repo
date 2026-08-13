package com.bouba.backend_trans.seuil.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.bouba.backend_trans.metrique.entity.TypeMetrique;
import com.bouba.backend_trans.seuil.entity.SeuilAlerte;

public class SeuilAlerteResponse {

	private UUID id;
	private TypeMetrique typeMetrique;
	private UUID equipementId;
	private String equipementNom;
	private BigDecimal avertissement;
	private BigDecimal critique;
	private int dureeSecondes;

	public static SeuilAlerteResponse fromEntity(SeuilAlerte seuil) {
		SeuilAlerteResponse response = new SeuilAlerteResponse();
		response.id = seuil.getId();
		response.typeMetrique = seuil.getTypeMetrique();
		response.equipementId = seuil.getEquipement() == null ? null : seuil.getEquipement().getId();
		response.equipementNom = seuil.getEquipement() == null ? null : seuil.getEquipement().getNom();
		response.avertissement = seuil.getAvertissement();
		response.critique = seuil.getCritique();
		response.dureeSecondes = seuil.getDureeSecondes();
		return response;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

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

	public String getEquipementNom() {
		return equipementNom;
	}

	public void setEquipementNom(String equipementNom) {
		this.equipementNom = equipementNom;
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

	public int getDureeSecondes() {
		return dureeSecondes;
	}

	public void setDureeSecondes(int dureeSecondes) {
		this.dureeSecondes = dureeSecondes;
	}
}
