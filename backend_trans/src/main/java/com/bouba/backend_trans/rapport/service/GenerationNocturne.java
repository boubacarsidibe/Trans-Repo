package com.bouba.backend_trans.rapport.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bouba.backend_trans.rapport.dto.RapportGenerateRequest;
import com.bouba.backend_trans.rapport.entity.Rapport;
import com.bouba.backend_trans.rapport.entity.TypeRapport;

/**
 * « Un rapport journalier est généré automatiquement chaque nuit et couvre la
 * journée précédente » (règle F8).
 */
@Component
public class GenerationNocturne {

	private static final Logger log = LoggerFactory.getLogger(GenerationNocturne.class);

	private final RapportService rapportService;

	public GenerationNocturne(RapportService rapportService) {
		this.rapportService = rapportService;
	}

	@Scheduled(cron = "${app.rapports.cron-journalier}")
	public void genererRapportDeLaVeille() {
		LocalDate veille = LocalDate.now().minusDays(1);

		RapportGenerateRequest demande = new RapportGenerateRequest();
		demande.setTypeRapport(TypeRapport.JOURNALIER);
		demande.setPeriodeDebut(veille.atStartOfDay());
		demande.setPeriodeFin(veille.plusDays(1).atStartOfDay());

		try {
			Rapport rapport = rapportService.generate(demande);
			log.info("Rapport journalier du {} généré ({}).", veille, rapport.getId());
		} catch (Exception ex) {
			log.error("Génération du rapport journalier du {} impossible : {}", veille, ex.getMessage());
		}
	}
}
