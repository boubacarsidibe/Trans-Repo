package com.bouba.backend_trans.maintenance.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bouba.backend_trans.maintenance.entity.FenetreMaintenance;

public interface FenetreMaintenanceRepository extends JpaRepository<FenetreMaintenance, UUID> {

	@EntityGraph(attributePaths = {"equipement", "creePar"})
	List<FenetreMaintenance> findByEquipementIdOrderByDateDebutDesc(UUID equipementId);

	/** Vrai si au moins une fenêtre de maintenance existe pour cet équipement — bloque sa suppression définitive. */
	boolean existsByEquipementId(UUID equipementId);

	/**
	 * Vrai si une fenêtre non annulée de cet équipement couvre l'instant donné.
	 * Utilisé par le moteur d'alertes (F6, issue #160) pour taire la création de
	 * nouvelles alertes pendant une intervention planifiée.
	 */
	boolean existsByEquipementIdAndAnnuleeFalseAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
			UUID equipementId, LocalDateTime instantDebut, LocalDateTime instantFin);
}
