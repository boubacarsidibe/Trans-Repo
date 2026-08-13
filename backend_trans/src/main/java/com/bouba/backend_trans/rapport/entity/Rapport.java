package com.bouba.backend_trans.rapport.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "rapports")
public class Rapport {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "type_rapport", nullable = false, length = 20)
	private TypeRapport typeRapport;

	@Column(name = "periode_debut", nullable = false)
	private LocalDateTime periodeDebut;

	@Column(name = "periode_fin", nullable = false)
	private LocalDateTime periodeFin;

	@Column(name = "chemin_fichier_pdf", length = 255)
	private String cheminFichierPdf;

	@Column(name = "date_generation", nullable = false)
	private LocalDateTime dateGeneration;

	@PrePersist
	void onCreate() {
		if (dateGeneration == null) {
			dateGeneration = LocalDateTime.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public TypeRapport getTypeRapport() {
		return typeRapport;
	}

	public void setTypeRapport(TypeRapport typeRapport) {
		this.typeRapport = typeRapport;
	}

	public LocalDateTime getPeriodeDebut() {
		return periodeDebut;
	}

	public void setPeriodeDebut(LocalDateTime periodeDebut) {
		this.periodeDebut = periodeDebut;
	}

	public LocalDateTime getPeriodeFin() {
		return periodeFin;
	}

	public void setPeriodeFin(LocalDateTime periodeFin) {
		this.periodeFin = periodeFin;
	}

	public String getCheminFichierPdf() {
		return cheminFichierPdf;
	}

	public void setCheminFichierPdf(String cheminFichierPdf) {
		this.cheminFichierPdf = cheminFichierPdf;
	}

	public LocalDateTime getDateGeneration() {
		return dateGeneration;
	}

	public void setDateGeneration(LocalDateTime dateGeneration) {
		this.dateGeneration = dateGeneration;
	}
}
