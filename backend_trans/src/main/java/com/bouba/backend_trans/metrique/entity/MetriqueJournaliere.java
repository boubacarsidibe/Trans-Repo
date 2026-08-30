package com.bouba.backend_trans.metrique.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Agrégat journalier, conservé au-delà de douze mois pour la tendance longue
 * durée (§6.10).
 */
@Entity
@Table(name = "metriques_journalieres",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_metrique_journaliere",
				columnNames = {"equipement_id", "type_metrique", "jour"}),
		indexes = @Index(name = "idx_metrique_journaliere_equipement", columnList = "equipement_id, jour"))
public class MetriqueJournaliere {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "equipement_id", nullable = false)
	private UUID equipementId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type_metrique", nullable = false, length = 40)
	private TypeMetrique typeMetrique;

	@Column(nullable = false)
	private LocalDate jour;

	@Column(precision = 18, scale = 4)
	private BigDecimal moyenne;

	@Column(precision = 18, scale = 4)
	private BigDecimal minimum;

	@Column(precision = 18, scale = 4)
	private BigDecimal maximum;

	@Column(name = "nombre_mesures", nullable = false)
	private long nombreMesures;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public UUID getEquipementId() {
		return equipementId;
	}

	public void setEquipementId(UUID equipementId) {
		this.equipementId = equipementId;
	}

	public TypeMetrique getTypeMetrique() {
		return typeMetrique;
	}

	public void setTypeMetrique(TypeMetrique typeMetrique) {
		this.typeMetrique = typeMetrique;
	}

	public LocalDate getJour() {
		return jour;
	}

	public void setJour(LocalDate jour) {
		this.jour = jour;
	}

	public BigDecimal getMoyenne() {
		return moyenne;
	}

	public void setMoyenne(BigDecimal moyenne) {
		this.moyenne = moyenne;
	}

	public BigDecimal getMinimum() {
		return minimum;
	}

	public void setMinimum(BigDecimal minimum) {
		this.minimum = minimum;
	}

	public BigDecimal getMaximum() {
		return maximum;
	}

	public void setMaximum(BigDecimal maximum) {
		this.maximum = maximum;
	}

	public long getNombreMesures() {
		return nombreMesures;
	}

	public void setNombreMesures(long nombreMesures) {
		this.nombreMesures = nombreMesures;
	}
}
