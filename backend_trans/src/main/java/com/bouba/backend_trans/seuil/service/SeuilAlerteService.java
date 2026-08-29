package com.bouba.backend_trans.seuil.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.audit.Auditable;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;
import com.bouba.backend_trans.seuil.dto.SeuilAlerteRequest;
import com.bouba.backend_trans.seuil.entity.SeuilAlerte;
import com.bouba.backend_trans.seuil.repository.SeuilAlerteRepository;

@Service
public class SeuilAlerteService {

	private final SeuilAlerteRepository seuilRepository;
	private final EquipementRepository equipementRepository;

	/**
	 * Le seuil est relu à chaque métrique ingérée : une centaine d'équipements
	 * qui remontent une trentaine de valeurs par minute (§13) feraient sinon
	 * quelques milliers d'allers-retours en base par minute pour une table qui
	 * ne change qu'à la main. Le cache est vidé en entier à chaque écriture —
	 * une invalidation fine n'apporterait rien sur un volume aussi petit.
	 */
	private final Map<String, Optional<Seuil>> cache = new ConcurrentHashMap<>();

	public SeuilAlerteService(SeuilAlerteRepository seuilRepository, EquipementRepository equipementRepository) {
		this.seuilRepository = seuilRepository;
		this.equipementRepository = equipementRepository;
	}

	/** Surcharge de l'équipement si elle existe, sinon défaut global, sinon {@code null}. */
	@Transactional(readOnly = true)
	public Seuil seuilEffectif(UUID equipementId, TypeMetrique typeMetrique) {
		return cache
				.computeIfAbsent(cle(equipementId, typeMetrique), ignore -> resoudre(equipementId, typeMetrique))
				.orElse(null);
	}

	@Transactional(readOnly = true)
	public List<SeuilAlerte> findAll() {
		return seuilRepository.findAllByOrderByTypeMetriqueAsc();
	}

	@Transactional(readOnly = true)
	public SeuilAlerte findById(UUID id) {
		return seuilRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Seuil introuvable."));
	}

	@Transactional
	@Auditable("CREATION_SEUIL")
	public SeuilAlerte create(SeuilAlerteRequest request) {
		valider(request);

		boolean existeDeja = request.getEquipementId() == null
				? seuilRepository.findByTypeMetriqueAndEquipementIsNull(request.getTypeMetrique()).isPresent()
				: seuilRepository
						.findByTypeMetriqueAndEquipementId(request.getTypeMetrique(), request.getEquipementId())
						.isPresent();
		if (existeDeja) {
			throw new IllegalArgumentException(
					"Un seuil existe déjà pour cette métrique et ce périmètre. Modifiez-le plutôt que d'en créer un second.");
		}

		SeuilAlerte seuil = new SeuilAlerte();
		seuil.setTypeMetrique(request.getTypeMetrique());
		seuil.setEquipement(equipementDe(request));
		appliquer(request, seuil);
		SeuilAlerte enregistre = seuilRepository.save(seuil);

		cache.clear();
		return enregistre;
	}

	@Transactional
	@Auditable("MODIFICATION_SEUIL")
	public SeuilAlerte update(UUID id, SeuilAlerteRequest request) {
		valider(request);

		SeuilAlerte seuil = findById(id);
		appliquer(request, seuil);
		SeuilAlerte enregistre = seuilRepository.save(seuil);

		cache.clear();
		return enregistre;
	}

	@Transactional
	@Auditable("SUPPRESSION_SEUIL")
	public void delete(UUID id) {
		seuilRepository.delete(findById(id));
		cache.clear();
	}

	/** Réservé à l'amorçage des défauts : n'écrase jamais un seuil déjà présent. */
	@Transactional
	public void creerDefautSiAbsent(TypeMetrique typeMetrique, BigDecimal avertissement, BigDecimal critique, int dureeSecondes) {
		if (seuilRepository.existsByTypeMetriqueAndEquipementIsNull(typeMetrique)) {
			return;
		}

		SeuilAlerte seuil = new SeuilAlerte();
		seuil.setTypeMetrique(typeMetrique);
		seuil.setAvertissement(avertissement);
		seuil.setCritique(critique);
		seuil.setDureeSecondes(dureeSecondes);
		seuilRepository.save(seuil);

		cache.clear();
	}

	private Optional<Seuil> resoudre(UUID equipementId, TypeMetrique typeMetrique) {
		return seuilRepository.findByTypeMetriqueAndEquipementId(typeMetrique, equipementId)
				.or(() -> seuilRepository.findByTypeMetriqueAndEquipementIsNull(typeMetrique))
				.map(s -> new Seuil(s.getAvertissement(), s.getCritique(), s.getDureeSecondes()));
	}

	private void appliquer(SeuilAlerteRequest request, SeuilAlerte seuil) {
		seuil.setAvertissement(request.getAvertissement());
		seuil.setCritique(request.getCritique());
		seuil.setDureeSecondes(request.getDureeSecondes() == null ? 0 : request.getDureeSecondes());
	}

	private Equipement equipementDe(SeuilAlerteRequest request) {
		if (request.getEquipementId() == null) {
			return null;
		}
		return equipementRepository.findById(request.getEquipementId())
				.orElseThrow(() -> new IllegalArgumentException("Équipement introuvable."));
	}

	private void valider(SeuilAlerteRequest request) {
		if (request.getAvertissement() == null && request.getCritique() == null) {
			throw new IllegalArgumentException("Un seuil doit porter au moins une valeur d'avertissement ou de critique.");
		}
		if (request.getAvertissement() != null
				&& request.getCritique() != null
				&& request.getAvertissement().compareTo(request.getCritique()) > 0) {
			throw new IllegalArgumentException("Le seuil d'avertissement ne peut pas dépasser le seuil critique.");
		}
		if (request.getDureeSecondes() != null && request.getDureeSecondes() < 0) {
			throw new IllegalArgumentException("La durée de maintien ne peut pas être négative.");
		}
	}

	private String cle(UUID equipementId, TypeMetrique typeMetrique) {
		return typeMetrique.name() + "|" + equipementId;
	}
}
