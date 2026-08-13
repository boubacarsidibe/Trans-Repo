package com.bouba.backend_trans.alerte.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.StatutAlerte;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;

public class AlerteResponse {

	private UUID id;
	private UUID equipementId;
	private String equipementNom;
	private TypeAnomalie typeAnomalie;
	private Severite severite;
	private StatutAlerte statut;
	private LocalDateTime dateDeclenchement;
	private LocalDateTime dateResolution;
	private String utilisateurPriseEnCharge;

	public static AlerteResponse fromEntity(Alerte alerte) {
		AlerteResponse response = new AlerteResponse();
		response.id = alerte.getId();
		response.equipementId = alerte.getEquipement().getId();
		response.equipementNom = alerte.getEquipement().getNom();
		response.typeAnomalie = alerte.getTypeAnomalie();
		response.severite = alerte.getSeverite();
		response.statut = alerte.getStatut();
		response.dateDeclenchement = alerte.getDateDeclenchement();
		response.dateResolution = alerte.getDateResolution();
		response.utilisateurPriseEnCharge = alerte.getUtilisateurPriseEnCharge() == null
				? null
				: alerte.getUtilisateurPriseEnCharge().getEmail();
		return response;
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

	public TypeAnomalie getTypeAnomalie() {
		return typeAnomalie;
	}

	public void setTypeAnomalie(TypeAnomalie typeAnomalie) {
		this.typeAnomalie = typeAnomalie;
	}

	public Severite getSeverite() {
		return severite;
	}

	public void setSeverite(Severite severite) {
		this.severite = severite;
	}

	public StatutAlerte getStatut() {
		return statut;
	}

	public void setStatut(StatutAlerte statut) {
		this.statut = statut;
	}

	public LocalDateTime getDateDeclenchement() {
		return dateDeclenchement;
	}

	public void setDateDeclenchement(LocalDateTime dateDeclenchement) {
		this.dateDeclenchement = dateDeclenchement;
	}

	public LocalDateTime getDateResolution() {
		return dateResolution;
	}

	public void setDateResolution(LocalDateTime dateResolution) {
		this.dateResolution = dateResolution;
	}

	public String getUtilisateurPriseEnCharge() {
		return utilisateurPriseEnCharge;
	}

	public void setUtilisateurPriseEnCharge(String utilisateurPriseEnCharge) {
		this.utilisateurPriseEnCharge = utilisateurPriseEnCharge;
	}
}
