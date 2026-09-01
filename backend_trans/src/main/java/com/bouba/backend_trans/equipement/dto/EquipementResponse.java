package com.bouba.backend_trans.equipement.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.TypeEquipement;
import com.fasterxml.jackson.annotation.JsonInclude;

public class EquipementResponse {

	private UUID id;
	private String nom;
	private String adresseIp;
	private TypeEquipement type;
	private String localisation;
	private EtatEquipement etat;
	private String description;
	private LocalDateTime derniereMesure;
	private UUID dependDeId;
	private String dependDeNom;
	private String snmpCommunity;
	private Integer snmpPort;
	private Integer interfaceIndex;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String cleApi;

	public static EquipementResponse fromEntity(Equipement equipement) {
		return fromEntity(equipement, false);
	}

	public static EquipementResponse fromEntity(Equipement equipement, boolean includeApiKey) {
		EquipementResponse response = new EquipementResponse();
		response.id = equipement.getId();
		response.nom = equipement.getNom();
		response.adresseIp = equipement.getAdresseIp();
		response.type = equipement.getType();
		response.localisation = equipement.getLocalisation();
		response.etat = equipement.getEtat();
		response.description = equipement.getDescription();
		response.derniereMesure = equipement.getDerniereMesure();
		response.dependDeId = equipement.getDependDe() == null ? null : equipement.getDependDe().getId();
		response.dependDeNom = equipement.getDependDe() == null ? null : equipement.getDependDe().getNom();
		response.snmpCommunity = equipement.getSnmpCommunity();
		response.snmpPort = equipement.getSnmpPort();
		response.interfaceIndex = equipement.getInterfaceIndex();
		if (includeApiKey) {
			response.cleApi = equipement.getCleApi();
		}
		return response;
	}

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

	public UUID getDependDeId() {
		return dependDeId;
	}

	public void setDependDeId(UUID dependDeId) {
		this.dependDeId = dependDeId;
	}

	public String getDependDeNom() {
		return dependDeNom;
	}

	public void setDependDeNom(String dependDeNom) {
		this.dependDeNom = dependDeNom;
	}

	public LocalDateTime getDerniereMesure() {
		return derniereMesure;
	}

	public void setDerniereMesure(LocalDateTime derniereMesure) {
		this.derniereMesure = derniereMesure;
	}

	public String getCleApi() {
		return cleApi;
	}

	public void setCleApi(String cleApi) {
		this.cleApi = cleApi;
	}

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
}
