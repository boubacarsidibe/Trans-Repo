package com.bouba.backend_trans.collecteur;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bouba.backend_trans.collecteur.entity.Collecteur;
import com.bouba.backend_trans.collecteur.repository.CollecteurRepository;
import com.bouba.backend_trans.websocket.DiffusionSupervision;
import com.bouba.backend_trans.websocket.TypeEvenement;

/**
 * Surveille le silence du collecteur réseau actif (issue #157).
 *
 * <p>Le collecteur réseau (agent/network/network_collector.py) est un point
 * de panne unique pour toute la supervision réseau. En redondance simple
 * (deux instances, une seule active), plus rien ne remonte si cette instance
 * active s'arrête — la secondaire, elle, ne prend le relais qu'après
 * plusieurs cycles sans heartbeat de sa part. Ce watchdog applique à
 * l'instance active du collecteur le même principe que
 * {@code DisponibiliteWatchdog} applique aux équipements (F3/F4) : au bout
 * de {@code cyclesToleres} cycles sans heartbeat, elle est déclarée
 * indisponible et un événement temps réel est diffusé.
 */
@Component
public class CollecteurWatchdog {

	private static final Logger log = LoggerFactory.getLogger(CollecteurWatchdog.class);

	private final CollecteurRepository collecteurRepository;
	private final DiffusionSupervision diffusionSupervision;

	private final int intervalleCollecteSecondes;
	private final int cyclesToleres;

	/**
	 * Dernier état connu, pour n'émettre un événement qu'au changement — le
	 * même principe que {@code DisponibiliteWatchdog.derniereDisponibilite}.
	 * {@code null} : aucune instance ne s'est encore déclarée active.
	 */
	private final AtomicReference<Boolean> derniereDisponibilite = new AtomicReference<>();

	public CollecteurWatchdog(
			CollecteurRepository collecteurRepository,
			DiffusionSupervision diffusionSupervision,
			@Value("${app.collecte.intervalle-secondes}") int intervalleCollecteSecondes,
			@Value("${app.watchdog.cycles-toleres}") int cyclesToleres
	) {
		this.collecteurRepository = collecteurRepository;
		this.diffusionSupervision = diffusionSupervision;
		this.intervalleCollecteSecondes = intervalleCollecteSecondes;
		this.cyclesToleres = cyclesToleres;
	}

	@Scheduled(fixedDelayString = "${app.watchdog.periode-ms}")
	public void verifierCollecteurActif() {
		Optional<Collecteur> collecteurActif = collecteurRepository.findFirstByActifTrueOrderByDernierHeartbeatDesc();
		if (collecteurActif.isEmpty()) {
			// Aucune instance ne s'est encore signalée active : redondance non
			// configurée (COLLECTOR_ID/COLLECTOR_API_KEY absents), rien à surveiller.
			return;
		}

		Collecteur collecteur = collecteurActif.get();
		LocalDateTime silenceDepuis = LocalDateTime.now()
				.minusSeconds((long) intervalleCollecteSecondes * cyclesToleres);

		boolean disponible = collecteur.getDernierHeartbeat().isAfter(silenceDepuis);

		signalerSiChangement(collecteur, disponible);
	}

	private void signalerSiChangement(Collecteur collecteur, boolean disponible) {
		Boolean precedente = derniereDisponibilite.getAndSet(disponible);
		if (precedente != null && precedente == disponible) {
			return;
		}

		if (!disponible) {
			log.warn("Collecteur reseau {} muet depuis {} : declare indisponible.",
					collecteur.getCollecteurId(), collecteur.getDernierHeartbeat());
		}

		diffusionSupervision.publier(
				TypeEvenement.COLLECTOR_STATUS_CHANGED,
				new CollecteurEvenement(
						collecteur.getCollecteurId(),
						disponible,
						collecteur.getDernierHeartbeat()));
	}
}
