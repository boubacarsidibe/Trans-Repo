package com.bouba.backend_trans.equipement.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.equipement.dto.EquipementRequest;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;

@Service
public class EquipementService {

	private final EquipementRepository equipementRepository;

	public EquipementService(EquipementRepository equipementRepository) {
		this.equipementRepository = equipementRepository;
	}

	@Transactional(readOnly = true)
	public List<Equipement> findAll() {
		return equipementRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Equipement findById(UUID id) {
		return equipementRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Équipement introuvable."));
	}

	@Transactional
	public Equipement create(EquipementRequest request) {
		if (equipementRepository.existsByAdresseIp(request.getAdresseIp())) {
			throw new IllegalStateException("Un équipement avec cette adresse IP existe déjà.");
		}

		Equipement equipement = new Equipement();
		applyRequest(equipement, request);
		if (equipement.getCleApi() == null || equipement.getCleApi().isBlank()) {
			equipement.setCleApi(generateApiKey());
		}
		return equipementRepository.save(equipement);
	}

	@Transactional
	public Equipement update(UUID id, EquipementRequest request) {
		Equipement equipement = findById(id);

		if (!equipement.getAdresseIp().equals(request.getAdresseIp())
				&& equipementRepository.existsByAdresseIp(request.getAdresseIp())) {
			throw new IllegalStateException("Un équipement avec cette adresse IP existe déjà.");
		}

		applyRequest(equipement, request);
		return equipementRepository.save(equipement);
	}

	@Transactional
	public void archive(UUID id) {
		Equipement equipement = findById(id);
		equipement.setEtat(EtatEquipement.INACTIF);
		equipementRepository.save(equipement);
	}

	private void applyRequest(Equipement equipement, EquipementRequest request) {
		equipement.setNom(request.getNom());
		equipement.setAdresseIp(request.getAdresseIp());
		equipement.setType(request.getType());
		equipement.setLocalisation(request.getLocalisation());
		equipement.setDescription(request.getDescription());
		equipement.setEtat(request.getEtat() == null ? EtatEquipement.ACTIF : request.getEtat());
		equipement.setDependDe(resoudreDependance(equipement, request.getDependDeId()));
		if (request.getCleApi() != null && !request.getCleApi().isBlank()) {
			equipement.setCleApi(request.getCleApi());
		}
	}

	/**
	 * Résout l'équipement parent en refusant les chaînes circulaires : une boucle
	 * de dépendance rendrait la suppression des alertes en cascade indécidable.
	 */
	private Equipement resoudreDependance(Equipement equipement, UUID dependDeId) {
		if (dependDeId == null) {
			return null;
		}
		if (dependDeId.equals(equipement.getId())) {
			throw new IllegalArgumentException("Un équipement ne peut pas dépendre de lui-même.");
		}

		Equipement parent = findById(dependDeId);

		for (Equipement ancetre = parent; ancetre != null; ancetre = ancetre.getDependDe()) {
			if (ancetre.getId().equals(equipement.getId())) {
				throw new IllegalArgumentException(
						"Cette dépendance formerait une boucle : " + parent.getNom()
								+ " dépend déjà, directement ou non, de " + equipement.getNom() + ".");
			}
		}

		return parent;
	}

	private String generateApiKey() {
		return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
	}
}
