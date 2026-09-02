package com.bouba.backend_trans.maintenance.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.equipement.entity.Equipement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Fenêtre de maintenance / silence programmé sur un équipement (issue #160).
 *
 * <p>Pendant l'intervalle {@code [dateDebut, dateFin]}, le moteur d'alertes
 * (F6, voir {@code MetriqueSeuilEvaluator} et {@code AlerteService}) ne
 * déclenche plus de <strong>nouvelle</strong> alerte pour cet équipement. Les
 * alertes déjà ouvertes avant l'entrée en fenêtre ne sont ni closes ni
 * modifiées — seule la création est bloquée, exactement comme demandé dans
 * l'issue.
 */
@Entity
@Table(name = "fenetres_maintenance", indexes = {
		@Index(name = "idx_fenetre_maintenance_equipement", columnList = "equipement_id")
})
public class FenetreMaintenance {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "equipement_id", nullable = false)
	private Equipement equipement;

	@Column(name = "date_debut", nullable = false)
	private LocalDateTime dateDebut;

	@Column(name = "date_fin", nullable = false)
	private LocalDateTime dateFin;

	/** Administrateur ou technicien à l'origine de la fenêtre (RBAC, §4.4). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cree_par_id", nullable = false)
	private AppUser creePar;

	@Column(columnDefinition = "TEXT")
	private String commentaire;

	/**
	 * Annulation logique : une fenêtre annulée n'est plus prise en compte par le
	 * moteur d'alertes, mais reste conservée pour l'historique plutôt que
	 * supprimée.
	 */
	@Column(nullable = false)
	private boolean annulee = false;

	@Column(name = "creee_le", nullable = false)
	private LocalDateTime creeeLe;

	@PrePersist
	void onCreate() {
		if (creeeLe == null) {
			creeeLe = LocalDateTime.now();
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

	public AppUser getCreePar() {
		return creePar;
	}

	public void setCreePar(AppUser creePar) {
		this.creePar = creePar;
	}

	public String getCommentaire() {
		return commentaire;
	}

	public void setCommentaire(String commentaire) {
		this.commentaire = commentaire;
	}

	public boolean isAnnulee() {
		return annulee;
	}

	public void setAnnulee(boolean annulee) {
		this.annulee = annulee;
	}

	public LocalDateTime getCreeeLe() {
		return creeeLe;
	}

	public void setCreeeLe(LocalDateTime creeeLe) {
		this.creeeLe = creeeLe;
	}
}
