package com.bouba.backend_trans.equipement.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.alerte.repository.AlerteRepository;
import com.bouba.backend_trans.audit.Auditable;
import com.bouba.backend_trans.equipement.dto.EquipementRequest;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.maintenance.repository.FenetreMaintenanceRepository;
import com.bouba.backend_trans.metrique.repository.MetriqueRepository;
import com.bouba.backend_trans.seuil.repository.SeuilAlerteRepository;

@Service
public class EquipementService {

	private final EquipementRepository equipementRepository;
	private final MetriqueRepository metriqueRepository;
	private final AlerteRepository alerteRepository;
	private final SeuilAlerteRepository seuilAlerteRepository;
	private final FenetreMaintenanceRepository fenetreMaintenanceRepository;

	public EquipementService(EquipementRepository equipementRepository, MetriqueRepository metriqueRepository,
			AlerteRepository alerteRepository, SeuilAlerteRepository seuilAlerteRepository,
			FenetreMaintenanceRepository fenetreMaintenanceRepository) {
		this.equipementRepository = equipementRepository;
		this.metriqueRepository = metriqueRepository;
		this.alerteRepository = alerteRepository;
		this.seuilAlerteRepository = seuilAlerteRepository;
		this.fenetreMaintenanceRepository = fenetreMaintenanceRepository;
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
	@Auditable("CREATION_EQUIPEMENT")
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
	@Auditable("MODIFICATION_EQUIPEMENT")
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
	@Auditable("ARCHIVAGE_EQUIPEMENT")
	public void archive(UUID id) {
		Equipement equipement = findById(id);
		equipement.setEtat(EtatEquipement.INACTIF);
		equipementRepository.save(equipement);
	}

	/**
	 * Suppression réelle de la ligne (issue #177), à l'inverse de {@link #archive}
	 * qui ne fait que masquer l'équipement. N'est autorisée que si l'équipement
	 * ne conserve strictement aucune trace : la supprimer casserait sinon les
	 * métriques/alertes/seuils/fenêtres de maintenance qui le référencent par
	 * clé étrangère, ou les équipements qui en dépendent.
	 */
	@Transactional
	@Auditable("SUPPRESSION_EQUIPEMENT")
	public void supprimerDefinitivement(UUID id) {
		Equipement equipement = findById(id);

		List<String> blocages = new ArrayList<>();
		if (metriqueRepository.existsByEquipementId(id)) {
			blocages.add("des métriques");
		}
		if (alerteRepository.existsByEquipementId(id)) {
			blocages.add("des alertes");
		}
		if (seuilAlerteRepository.existsByEquipementId(id)) {
			blocages.add("des seuils d'alerte");
		}
		if (fenetreMaintenanceRepository.existsByEquipementId(id)) {
			blocages.add("des fenêtres de maintenance");
		}
		if (equipementRepository.existsByDependDeId(id)) {
			blocages.add("des équipements qui en dépendent");
		}

		if (!blocages.isEmpty()) {
			throw new IllegalStateException(
					"Impossible de supprimer définitivement " + equipement.getNom() + " : il conserve "
							+ String.join(", ", blocages) + ". Archivez-le à la place.");
		}

		equipementRepository.delete(equipement);
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
