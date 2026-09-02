package com.bouba.backend_trans.maintenance.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bouba.backend_trans.maintenance.entity.FenetreMaintenance;

public class FenetreMaintenanceResponse {

	private UUID id;
	private UUID equipementId;
	private String equipementNom;
	private LocalDateTime dateDebut;
	private LocalDateTime dateFin;
	private String commentaire;
	private Long creeParId;
	private String creeParUsername;
	private boolean annulee;
	private boolean active;
	private LocalDateTime creeeLe;

	public static FenetreMaintenanceResponse fromEntity(FenetreMaintenance fenetre) {
		FenetreMaintenanceResponse response = new FenetreMaintenanceResponse();
		response.id = fenetre.getId();
		response.equipementId = fenetre.getEquipement().getId();
		response.equipementNom = fenetre.getEquipement().getNom();
		response.dateDebut = fenetre.getDateDebut();
		response.dateFin = fenetre.getDateFin();
		response.commentaire = fenetre.getCommentaire();
		response.creeParId = fenetre.getCreePar().getId();
		response.creeParUsername = fenetre.getCreePar().getUsername();
		response.annulee = fenetre.isAnnulee();
		response.active = estActive(fenetre);
		response.creeeLe = fenetre.getCreeeLe();
		return response;
	}

	private static boolean estActive(FenetreMaintenance fenetre) {
		if (fenetre.isAnnulee()) {
			return false;
		}
		LocalDateTime maintenant = LocalDateTime.now();
		return !maintenant.isBefore(fenetre.getDateDebut()) && !maintenant.isAfter(fenetre.getDateFin());
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
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

	public LocalDateTime getDateDebut() {
		return dateDebut;
	}

	public void setDateDebut(LocalDateTime dateDebut) {
		this.dateDebut = dateDebut;
	}

	public LocalDateTime getDateFin() {
		return dateFin;
	}

	public void setDateFin(LocalDateTime dateFin) {
		this.dateFin = dateFin;
	}

	public String getCommentaire() {
		return commentaire;
	}

	public void setCommentaire(String commentaire) {
		this.commentaire = commentaire;
	}

	public Long getCreeParId() {
		return creeParId;
	}

	public void setCreeParId(Long creeParId) {
		this.creeParId = creeParId;
	}

	public String getCreeParUsername() {
		return creeParUsername;
	}

	public void setCreeParUsername(String creeParUsername) {
		this.creeParUsername = creeParUsername;
	}

	public boolean isAnnulee() {
		return annulee;
	}

	public void setAnnulee(boolean annulee) {
		this.annulee = annulee;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public LocalDateTime getCreeeLe() {
		return creeeLe;
	}

	public void setCreeeLe(LocalDateTime creeeLe) {
		this.creeeLe = creeeLe;
	}
}
