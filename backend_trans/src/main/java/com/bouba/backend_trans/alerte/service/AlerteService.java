package com.bouba.backend_trans.alerte.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.StatutAlerte;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;
import com.bouba.backend_trans.alerte.repository.AlerteRepository;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.equipement.entity.Equipement;

@Service
public class AlerteService {

	private final AlerteRepository alerteRepository;

	public AlerteService(AlerteRepository alerteRepository) {
		this.alerteRepository = alerteRepository;
	}

	@Transactional(readOnly = true)
	public List<Alerte> findAll() {
		return alerteRepository.findAll();
	}

	@Transactional(readOnly = true)
	public List<Alerte> findByStatut(StatutAlerte statut) {
		return alerteRepository.findByStatutOrderByDateDeclenchementDesc(statut);
	}

	@Transactional(readOnly = true)
	public Alerte findById(UUID id) {
		return alerteRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Alerte introuvable."));
	}

	/**
	 * Crée une nouvelle alerte si aucune alerte de même nature n'est déjà active
	 * pour cet équipement (anti-répétition, cahier des charges §11.4).
	 */
	@Transactional
	public void declencherSiAbsente(Equipement equipement, TypeAnomalie typeAnomalie, Severite severite) {
		boolean dejaActive = alerteRepository
				.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
						equipement.getId(), typeAnomalie, StatutAlerte.RESOLUE)
				.isPresent();
		if (dejaActive) {
			return;
		}

		Alerte alerte = new Alerte();
		alerte.setEquipement(equipement);
		alerte.setTypeAnomalie(typeAnomalie);
		alerte.setSeverite(severite);
		alerte.setStatut(StatutAlerte.DECLENCHEE);
		alerteRepository.save(alerte);
	}

	/**
	 * Clôture automatiquement l'alerte active pour ce couple équipement/anomalie
	 * lorsque la métrique repasse sous le seuil.
	 */
	@Transactional
	public void resoudreSiActive(Equipement equipement, TypeAnomalie typeAnomalie) {
		alerteRepository
				.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
						equipement.getId(), typeAnomalie, StatutAlerte.RESOLUE)
				.ifPresent(alerte -> {
					alerte.setStatut(StatutAlerte.RESOLUE);
					alerte.setDateResolution(LocalDateTime.now());
					alerteRepository.save(alerte);
				});
	}

	@Transactional
	public Alerte acknowledge(UUID id, AppUser utilisateur) {
		Alerte alerte = findById(id);
		alerte.setStatut(StatutAlerte.PRISE_EN_COMPTE);
		alerte.setUtilisateurPriseEnCharge(utilisateur);
		return alerteRepository.save(alerte);
	}

	@Transactional
	public Alerte resolve(UUID id) {
		Alerte alerte = findById(id);
		alerte.setStatut(StatutAlerte.RESOLUE);
		alerte.setDateResolution(LocalDateTime.now());
		return alerteRepository.save(alerte);
	}
}
