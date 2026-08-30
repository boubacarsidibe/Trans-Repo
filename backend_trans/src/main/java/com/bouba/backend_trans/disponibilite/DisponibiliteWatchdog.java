package com.bouba.backend_trans.disponibilite;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;
import com.bouba.backend_trans.alerte.service.AlerteService;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.websocket.DiffusionSupervision;
import com.bouba.backend_trans.websocket.TypeEvenement;

/**
 * Surveille le silence des équipements.
 *
 * <p>Le moteur de seuils ne réagit qu'aux métriques <em>reçues</em> : un serveur
 * qui s'arrête net n'en produit plus aucune et passerait donc inaperçu. Les
 * règles F3 et F4 exigent l'inverse — au bout de trois cycles de collecte sans
 * remontée, l'équipement est déclaré indisponible.
 */
@Component
public class DisponibiliteWatchdog {

	private static final Logger log = LoggerFactory.getLogger(DisponibiliteWatchdog.class);

	private final EquipementRepository equipementRepository;
	private final AlerteService alerteService;
	private final DiffusionSupervision diffusionSupervision;

	private final int intervalleCollecteSecondes;
	private final int cyclesToleres;

	/**
	 * Dernière disponibilité connue par équipement, pour n'émettre un événement
	 * qu'au <em>changement</em> — le balayage tourne en continu, la console ne
	 * doit pas recevoir le même constat toutes les trente secondes.
	 */
	private final Map<UUID, Boolean> derniereDisponibilite = new ConcurrentHashMap<>();

	public DisponibiliteWatchdog(
			EquipementRepository equipementRepository,
			AlerteService alerteService,
			DiffusionSupervision diffusionSupervision,
			@Value("${app.collecte.intervalle-secondes}") int intervalleCollecteSecondes,
			@Value("${app.watchdog.cycles-toleres}") int cyclesToleres
	) {
		this.equipementRepository = equipementRepository;
		this.alerteService = alerteService;
		this.diffusionSupervision = diffusionSupervision;
		this.intervalleCollecteSecondes = intervalleCollecteSecondes;
		this.cyclesToleres = cyclesToleres;
	}

	@Scheduled(fixedDelayString = "${app.watchdog.periode-ms}")
	@Transactional
	public void verifierDisponibilite() {
		LocalDateTime silenceDepuis = LocalDateTime.now()
				.minusSeconds((long) intervalleCollecteSecondes * cyclesToleres);

		// Un équipement archivé (INACTIF) est sorti du parc supervisé : son
		// silence est attendu et ne doit pas produire d'alerte.
		List<Equipement> supervises = equipementRepository.findByEtatNot(EtatEquipement.INACTIF);

		for (Equipement equipement : supervises) {
			LocalDateTime derniereMesure = equipement.getDerniereMesure();
			if (derniereMesure == null) {
				// Déclaré mais jamais équipé d'agent : rien à conclure.
				continue;
			}

			boolean disponible = derniereMesure.isAfter(silenceDepuis);

			if (disponible) {
				alerteService.resoudreSiActive(equipement, TypeAnomalie.INDISPONIBILITE);
			} else {
				alerteService.declencherOuEleverSeverite(
						equipement, TypeAnomalie.INDISPONIBILITE, Severite.CRITIQUE);
			}

			signalerSiChangement(equipement, disponible);
		}
	}

	private void signalerSiChangement(Equipement equipement, boolean disponible) {
		Boolean precedente = derniereDisponibilite.put(equipement.getId(), disponible);
		if (precedente != null && precedente == disponible) {
			return;
		}

		if (!disponible) {
			log.warn("Équipement {} muet depuis {} : déclaré indisponible.",
					equipement.getNom(), equipement.getDerniereMesure());
		}

		diffusionSupervision.publier(
				TypeEvenement.EQUIPMENT_STATUS_CHANGED,
				new DisponibiliteEvenement(
						equipement.getId(),
						equipement.getNom(),
						disponible,
						equipement.getDerniereMesure()));
	}
}
