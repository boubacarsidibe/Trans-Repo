package com.bouba.backend_trans.maintenance.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.audit.Auditable;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.maintenance.dto.FenetreMaintenanceRequest;
import com.bouba.backend_trans.maintenance.entity.FenetreMaintenance;
import com.bouba.backend_trans.maintenance.repository.FenetreMaintenanceRepository;

/**
 * Fenêtres de maintenance / silence programmé sur un équipement (issue #160).
 *
 * <p>{@link #estActive(UUID)} est la seule dépendance que le moteur d'alertes
 * ({@code AlerteService}) prend sur ce module : une fenêtre active tait la
 * création de nouvelles alertes pour l'équipement concerné, sans toucher à
 * celles déjà ouvertes.
 */
@Service
public class FenetreMaintenanceService {

	private final FenetreMaintenanceRepository fenetreMaintenanceRepository;
	private final EquipementRepository equipementRepository;

	public FenetreMaintenanceService(
			FenetreMaintenanceRepository fenetreMaintenanceRepository,
			EquipementRepository equipementRepository
	) {
		this.fenetreMaintenanceRepository = fenetreMaintenanceRepository;
		this.equipementRepository = equipementRepository;
	}

	/** Vrai si une fenêtre non annulée de cet équipement couvre l'instant présent. */
	@Transactional(readOnly = true)
	public boolean estActive(UUID equipementId) {
		LocalDateTime maintenant = LocalDateTime.now();
		return fenetreMaintenanceRepository
				.existsByEquipementIdAndAnnuleeFalseAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
						equipementId, maintenant, maintenant);
	}

	@Transactional(readOnly = true)
	public List<FenetreMaintenance> findByEquipement(UUID equipementId) {
		equipementIntrouvableSiAbsent(equipementId);
		return fenetreMaintenanceRepository.findByEquipementIdOrderByDateDebutDesc(equipementId);
	}

	@Transactional
	@Auditable("CREATION_FENETRE_MAINTENANCE")
	public FenetreMaintenance create(UUID equipementId, FenetreMaintenanceRequest request, AppUser creePar) {
		valider(request);
		Equipement equipement = equipementIntrouvableSiAbsent(equipementId);

		FenetreMaintenance fenetre = new FenetreMaintenance();
		fenetre.setEquipement(equipement);
		fenetre.setDateDebut(request.getDateDebut());
		fenetre.setDateFin(request.getDateFin());
		fenetre.setCommentaire(request.getCommentaire());
		fenetre.setCreePar(creePar);

		return fenetreMaintenanceRepository.save(fenetre);
	}

	/** Annulation logique : la fenêtre reste conservée pour l'historique. */
	@Transactional
	@Auditable("ANNULATION_FENETRE_MAINTENANCE")
	public FenetreMaintenance annuler(UUID equipementId, UUID id) {
		FenetreMaintenance fenetre = fenetreMaintenanceRepository.findById(id)
				.filter(f -> f.getEquipement().getId().equals(equipementId))
				.orElseThrow(() -> new IllegalArgumentException("Fenêtre de maintenance introuvable."));

		if (fenetre.isAnnulee()) {
			throw new IllegalStateException("Cette fenêtre de maintenance est déjà annulée.");
		}

		fenetre.setAnnulee(true);
		return fenetreMaintenanceRepository.save(fenetre);
	}

	private void valider(FenetreMaintenanceRequest request) {
		if (request.getDateDebut() == null || request.getDateFin() == null) {
			throw new IllegalArgumentException("La date de début et la date de fin sont obligatoires.");
		}
		if (!request.getDateFin().isAfter(request.getDateDebut())) {
			throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");
		}
	}

	private Equipement equipementIntrouvableSiAbsent(UUID equipementId) {
		return equipementRepository.findById(equipementId)
				.orElseThrow(() -> new IllegalArgumentException("Équipement introuvable."));
	}
}
