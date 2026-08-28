package com.bouba.backend_trans.metrique.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * Moyenne horaire d'une métrique, conservée jusqu'à douze mois (§6.10).
 *
 * <p>L'équipement est référencé par son seul identifiant, sans association JPA :
 * ces lignes survivent à l'archivage d'un équipement, et une contrainte de clé
 * étrangère empêcherait de conserver l'historique d'un matériel retiré du parc.
 */
@Entity
@Table(name = "metriques_horaires",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_metrique_horaire",
				columnNames = {"equipement_id", "type_metrique", "heure"}),
		indexes = @Index(name = "idx_metrique_horaire_equipement", columnList = "equipement_id, heure"))
public class MetriqueHoraire {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "equipement_id", nullable = false)
	private UUID equipementId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type_metrique", nullable = false, length = 40)
	private TypeMetrique typeMetrique;

	@Column(nullable = false)
	private LocalDateTime heure;

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

	public LocalDateTime getHeure() {
		return heure;
	}

	public void setHeure(LocalDateTime heure) {
		this.heure = heure;
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
