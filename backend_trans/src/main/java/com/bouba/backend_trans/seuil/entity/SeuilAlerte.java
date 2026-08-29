package com.bouba.backend_trans.seuil.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Seuil de déclenchement d'alerte pour un type de métrique (§11.2).
 *
 * <p>Un seuil sans équipement est le <strong>défaut global</strong> ; un seuil
 * rattaché à un équipement le surcharge pour ce seul équipement, comme l'exige
 * la règle de gestion F6 (« configurables par équipement ou par défaut au
 * niveau global »). F3 désigne la règle de disponibilité (silence prolongé
 * ⇒ équipement indisponible), pas les seuils — la référence précédente à F3
 * ici était une erreur de numérotation.
 */
@Entity
@Table(name = "seuils_alerte", uniqueConstraints = @UniqueConstraint(
		name = "uk_seuil_type_equipement",
		columnNames = {"type_metrique", "equipement_id"}))
public class SeuilAlerte {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "type_metrique", nullable = false, length = 40)
	private TypeMetrique typeMetrique;

	/** {@code null} pour le défaut global. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "equipement_id")
	private Equipement equipement;

	@Column(precision = 18, scale = 4)
	private BigDecimal avertissement;

	@Column(precision = 18, scale = 4)
	private BigDecimal critique;

	/**
	 * Durée pendant laquelle le dépassement doit se maintenir avant de lever une
	 * alerte. Zéro déclenche sur la mesure instantanée. C'est ce qui traduit le
	 * « ≥ 80 % pendant 5 minutes » du §11.2 et évite qu'une pointe d'une seconde
	 * réveille une équipe.
	 */
	@Column(name = "duree_secondes", nullable = false)
	private int dureeSecondes;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public TypeMetrique getTypeMetrique() {
		return typeMetrique;
	}

	public void setTypeMetrique(TypeMetrique typeMetrique) {
		this.typeMetrique = typeMetrique;
	}

	public Equipement getEquipement() {
		return equipement;
	}

	public void setEquipement(Equipement equipement) {
		this.equipement = equipement;
	}

	public BigDecimal getAvertissement() {
		return avertissement;
	}

	public void setAvertissement(BigDecimal avertissement) {
		this.avertissement = avertissement;
	}

	public BigDecimal getCritique() {
		return critique;
	}

	public void setCritique(BigDecimal critique) {
		this.critique = critique;
	}

	public int getDureeSecondes() {
		return dureeSecondes;
	}

	public void setDureeSecondes(int dureeSecondes) {
		this.dureeSecondes = dureeSecondes;
	}
}
