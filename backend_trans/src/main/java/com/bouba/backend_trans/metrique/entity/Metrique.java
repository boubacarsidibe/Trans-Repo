package com.bouba.backend_trans.metrique.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
@Table(name = "metriques", indexes = {
		@Index(name = "idx_metrique_equipement_horodatage", columnList = "equipement_id, horodatage")
})
public class Metrique {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "equipement_id", nullable = false)
	private Equipement equipement;

	@Enumerated(EnumType.STRING)
	@Column(name = "type_metrique", nullable = false, length = 30)
	private TypeMetrique typeMetrique;

	@Column(nullable = false, precision = 18, scale = 4)
	private BigDecimal valeur;

	@Column(length = 20)
	private String unite;

	@Column(nullable = false)
	private LocalDateTime horodatage;

	@PrePersist
	void onCreate() {
		if (horodatage == null) {
			horodatage = LocalDateTime.now();
		}
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Equipement getEquipement() {
		return equipement;
	}

	public void setEquipement(Equipement equipement) {
		this.equipement = equipement;
	}

	public TypeMetrique getTypeMetrique() {
		return typeMetrique;
	}

	public void setTypeMetrique(TypeMetrique typeMetrique) {
		this.typeMetrique = typeMetrique;
	}

	public BigDecimal getValeur() {
		return valeur;
	}

	public void setValeur(BigDecimal valeur) {
		this.valeur = valeur;
	}

	public String getUnite() {
		return unite;
	}

	public void setUnite(String unite) {
		this.unite = unite;
	}

	public LocalDateTime getHorodatage() {
		return horodatage;
	}

	public void setHorodatage(LocalDateTime horodatage) {
		this.horodatage = horodatage;
	}
}
