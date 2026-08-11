package com.bouba.backend_trans.alerte.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.equipement.entity.Equipement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "alertes", indexes = {
		@Index(name = "idx_alerte_statut", columnList = "statut")
})
public class Alerte {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "equipement_id", nullable = false)
	private Equipement equipement;

	@Enumerated(EnumType.STRING)
	@Column(name = "type_anomalie", nullable = false, length = 30)
	private TypeAnomalie typeAnomalie;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Severite severite;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatutAlerte statut = StatutAlerte.DECLENCHEE;

	@Column(name = "date_declenchement", nullable = false)
	private LocalDateTime dateDeclenchement;

	@Column(name = "date_resolution")
	private LocalDateTime dateResolution;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "utilisateur_prise_en_charge")
	private AppUser utilisateurPriseEnCharge;

	@PrePersist
	void onCreate() {
		if (dateDeclenchement == null) {
			dateDeclenchement = LocalDateTime.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public Equipement getEquipement() {
		return equipement;
	}

	public void setEquipement(Equipement equipement) {
		this.equipement = equipement;
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

	public AppUser getUtilisateurPriseEnCharge() {
		return utilisateurPriseEnCharge;
	}

	public void setUtilisateurPriseEnCharge(AppUser utilisateurPriseEnCharge) {
		this.utilisateurPriseEnCharge = utilisateurPriseEnCharge;
	}
}
