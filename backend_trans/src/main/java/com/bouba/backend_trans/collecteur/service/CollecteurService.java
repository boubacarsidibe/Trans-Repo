package com.bouba.backend_trans.collecteur.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.collecteur.entity.Collecteur;
import com.bouba.backend_trans.collecteur.repository.CollecteurRepository;

/**
 * Enregistre les heartbeats des instances du collecteur réseau (issue #157).
 *
 * <p>Redondance simple : au plus une instance est active à la fois. Quand une
 * instance se déclare active, les autres lignes actives sont désactivées —
 * c'est ce qui matérialise la bascule primaire → secondaire pour
 * {@code CollecteurWatchdog}, qui ne surveille que l'instance active.
 */
@Service
public class CollecteurService {

	private final CollecteurRepository collecteurRepository;

	public CollecteurService(CollecteurRepository collecteurRepository) {
		this.collecteurRepository = collecteurRepository;
	}

	@Transactional
	public void enregistrerHeartbeat(String collecteurId, boolean actif) {
		LocalDateTime maintenant = LocalDateTime.now();

		if (actif) {
			collecteurRepository.findByActifTrueAndCollecteurIdNot(collecteurId)
					.forEach(autre -> autre.setActif(false));
		}

		Collecteur collecteur = collecteurRepository.findById(collecteurId)
				.orElseGet(() -> {
					Collecteur nouveau = new Collecteur();
					nouveau.setCollecteurId(collecteurId);
					return nouveau;
				});
		collecteur.setActif(actif);
		collecteur.setDernierHeartbeat(maintenant);
		collecteurRepository.save(collecteur);
	}
}
