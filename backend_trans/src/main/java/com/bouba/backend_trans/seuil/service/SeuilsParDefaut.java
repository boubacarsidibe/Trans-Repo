package com.bouba.backend_trans.seuil.service;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.bouba.backend_trans.metrique.entity.TypeMetrique;

/**
 * Amorce les seuils par défaut du §11.2 au démarrage.
 *
 * <p>L'opération est idempotente : un seuil déjà présent n'est jamais écrasé,
 * pour qu'un réglage fait par le CRI survive à un redémarrage.
 */
@Component
public class SeuilsParDefaut implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SeuilsParDefaut.class);

	/** Cinq minutes : la durée que le §11.2 impose au dépassement CPU. */
	private static final int CINQ_MINUTES = 300;

	private record Defaut(TypeMetrique typeMetrique, String avertissement, String critique, int dureeSecondes) {
	}

	private static final List<Defaut> DEFAUTS = List.of(
			// Tableau du §11.2
			new Defaut(TypeMetrique.CPU, "80", "95", CINQ_MINUTES),
			new Defaut(TypeMetrique.RAM, "80", "95", 0),
			new Defaut(TypeMetrique.DISQUE, "85", "95", 0),
			new Defaut(TypeMetrique.LATENCE, "150", "400", 0),
			new Defaut(TypeMetrique.TAUX_ERREUR, "1", "5", 0),

			// Métriques supplémentaires déjà collectées par les agents, hors
			// tableau §11.2 : valeurs reprises du moteur précédent.
			new Defaut(TypeMetrique.SWAP, "60", "90", 0),
			new Defaut(TypeMetrique.CHARGE_1MIN, "0.8", "1.0", 0),
			new Defaut(TypeMetrique.SERVICES_TCP_INDISPONIBLES, "1", "2", 0),
			new Defaut(TypeMetrique.DNS_LATENCE, "1000", "5000", 0),
			new Defaut(TypeMetrique.TEMPERATURE_MAX, "70", "85", 0));

	private final SeuilAlerteService seuilAlerteService;

	public SeuilsParDefaut(SeuilAlerteService seuilAlerteService) {
		this.seuilAlerteService = seuilAlerteService;
	}

	@Override
	public void run(ApplicationArguments args) {
		DEFAUTS.forEach(defaut -> seuilAlerteService.creerDefautSiAbsent(
				defaut.typeMetrique(),
				new BigDecimal(defaut.avertissement()),
				new BigDecimal(defaut.critique()),
				defaut.dureeSecondes()));

		log.info("Seuils par défaut vérifiés ({} types de métrique).", DEFAUTS.size());
	}
}
