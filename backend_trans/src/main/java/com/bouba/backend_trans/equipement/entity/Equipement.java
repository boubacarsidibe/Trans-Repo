package com.bouba.backend_trans.equipement.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "equipements")
public class Equipement {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 150)
	private String nom;

	@Column(name = "adresse_ip", nullable = false, unique = true, length = 45)
	private String adresseIp;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TypeEquipement type;

	@Column(length = 150)
	private String localisation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private EtatEquipement etat = EtatEquipement.ACTIF;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "cle_api", length = 255)
	private String cleApi;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getNom() {
		return nom;
	}

	public void setNom(String nom) {
		this.nom = nom;
	}

	public String getAdresseIp() {
		return adresseIp;
	}

	public void setAdresseIp(String adresseIp) {
		this.adresseIp = adresseIp;
	}

	public TypeEquipement getType() {
		return type;
	}

	public void setType(TypeEquipement type) {
		this.type = type;
	}

	public String getLocalisation() {
		return localisation;
	}

	public void setLocalisation(String localisation) {
		this.localisation = localisation;
	}

	public EtatEquipement getEtat() {
		return etat;
	}

	public void setEtat(EtatEquipement etat) {
		this.etat = etat;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getCleApi() {
		return cleApi;
	}

	public void setCleApi(String cleApi) {
		this.cleApi = cleApi;
	}
}
