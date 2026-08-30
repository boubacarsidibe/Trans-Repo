package com.bouba.backend_trans.notification;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.alerte.dto.AlerteResponse;
import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.StatutAlerte;
import com.bouba.backend_trans.alerte.repository.AlerteRepository;

/**
 * Relance périodique des alertes critiques que personne n'a prises en charge
 * (§11.4). Une alerte prise en compte cesse d'être rappelée : quelqu'un s'en
 * occupe, insister n'aiderait pas.
 */
@Component
public class RappelAlertesCritiques {

	private final AlerteRepository alerteRepository;
	private final NotificationAlerteService notificationAlerteService;
	private final int rappelMinutes;

	public RappelAlertesCritiques(
			AlerteRepository alerteRepository,
			NotificationAlerteService notificationAlerteService,
			@Value("${app.notifications.rappel-minutes}") int rappelMinutes
	) {
		this.alerteRepository = alerteRepository;
		this.notificationAlerteService = notificationAlerteService;
		this.rappelMinutes = rappelMinutes;
	}

	@Scheduled(fixedDelayString = "${app.notifications.rappel-periode-ms}")
	@Transactional
	public void rappeler() {
		LocalDateTime maintenant = LocalDateTime.now();
		LocalDateTime limite = maintenant.minusMinutes(rappelMinutes);

		List<Alerte> critiques =
				alerteRepository.findByStatutAndSeverite(StatutAlerte.DECLENCHEE, Severite.CRITIQUE);

		for (Alerte alerte : critiques) {
			LocalDateTime reference =
					alerte.getDernierRappel() != null ? alerte.getDernierRappel() : alerte.getDateDeclenchement();
			if (reference.isAfter(limite)) {
				continue;
			}

			alerte.setDernierRappel(maintenant);
			alerteRepository.save(alerte);
			notificationAlerteService.notifier(AlerteResponse.fromEntity(alerte), true);
		}
	}
}
