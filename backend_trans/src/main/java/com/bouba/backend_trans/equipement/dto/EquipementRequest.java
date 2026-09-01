package com.bouba.backend_trans.equipement.dto;

import java.util.UUID;

import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.TypeEquipement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class EquipementRequest {

	@NotBlank
	private String nom;

	@NotBlank
	private String adresseIp;

	@NotNull
	private TypeEquipement type;

	private String localisation;

	private EtatEquipement etat;

	private String description;

	private String cleApi;

	/** Équipement dont celui-ci dépend pour être joignable. */
	private UUID dependDeId;

	/** Paramètres SNMP (routeur/switch/point d'accès) ; ignorés pour un serveur. */
	private String snmpCommunity;

	private Integer snmpPort;

	private Integer interfaceIndex;

	public String getSnmpCommunity() {
		return snmpCommunity;
	}

	public void setSnmpCommunity(String snmpCommunity) {
		this.snmpCommunity = snmpCommunity;
	}

	public Integer getSnmpPort() {
		return snmpPort;
	}

	public void setSnmpPort(Integer snmpPort) {
		this.snmpPort = snmpPort;
	}

	public Integer getInterfaceIndex() {
		return interfaceIndex;
	}

	public void setInterfaceIndex(Integer interfaceIndex) {
		this.interfaceIndex = interfaceIndex;
	}

	public UUID getDependDeId() {
		return dependDeId;
	}

	public void setDependDeId(UUID dependDeId) {
		this.dependDeId = dependDeId;
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
