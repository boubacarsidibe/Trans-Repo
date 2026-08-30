package com.bouba.backend_trans.metrique.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.metrique.repository.MetriqueHoraireRepository;
import com.bouba.backend_trans.metrique.repository.MetriqueJournaliereRepository;

/**
 * Politique de rétention du §6.10 :
 *
 * <ul>
 *   <li>mesures brutes conservées 90 jours à leur résolution native ;</li>
 *   <li>au-delà, moyennes horaires conservées jusqu'à 12 mois ;</li>
 *   <li>au-delà de 12 mois, un agrégat journalier pour la tendance longue.</li>
 * </ul>
 *
 * <p>Chaque repli agrège <em>avant</em> de supprimer, dans la même transaction :
 * une interruption entre les deux perdrait définitivement l'historique.
 */
@Component
public class AgregationMetriques {

	private static final Logger log = LoggerFactory.getLogger(AgregationMetriques.class);

	private final MetriqueHoraireRepository horaireRepository;
	private final MetriqueJournaliereRepository journaliereRepository;
	private final int retentionBrutesJours;
	private final int retentionHoraireMois;

	public AgregationMetriques(
			MetriqueHoraireRepository horaireRepository,
			MetriqueJournaliereRepository journaliereRepository,
			@Value("${app.retention.metriques-brutes-jours}") int retentionBrutesJours,
			@Value("${app.retention.horaire-mois}") int retentionHoraireMois
	) {
		this.horaireRepository = horaireRepository;
		this.journaliereRepository = journaliereRepository;
		this.retentionBrutesJours = retentionBrutesJours;
		this.retentionHoraireMois = retentionHoraireMois;
	}

	@Scheduled(cron = "${app.retention.cron}")
	@Transactional
	public void appliquerLaRetention() {
		LocalDateTime limiteBrutes = LocalDateTime.now().minusDays(retentionBrutesJours);
		int heuresEcrites = horaireRepository.replierEnHeures(limiteBrutes);
		int brutesSupprimees = horaireRepository.supprimerBrutesAvant(limiteBrutes);

		LocalDateTime limiteHoraires = LocalDateTime.now().minusMonths(retentionHoraireMois);
		int joursEcrits = journaliereRepository.replierEnJours(limiteHoraires);
		int horairesSupprimees = journaliereRepository.supprimerHorairesAvant(limiteHoraires);

		log.info(
				"Rétention appliquée : {} moyennes horaires écrites, {} mesures brutes supprimées, "
						+ "{} agrégats journaliers écrits, {} moyennes horaires supprimées.",
				heuresEcrites, brutesSupprimees, joursEcrits, horairesSupprimees);
	}
}
