package com.bouba.backend_trans.rapport.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;
import com.bouba.backend_trans.alerte.repository.AlerteRepository;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.rapport.service.SyntheseRapport.EquipementSollicite;

/** Compile les chiffres d'un rapport sur une période donnée. */
@Component
public class RapportCalculateur {

	private static final int NOMBRE_EQUIPEMENTS_CITES = 5;

	private final AlerteRepository alerteRepository;
	private final EquipementRepository equipementRepository;

	public RapportCalculateur(AlerteRepository alerteRepository, EquipementRepository equipementRepository) {
		this.alerteRepository = alerteRepository;
		this.equipementRepository = equipementRepository;
	}

	@Transactional(readOnly = true)
	public SyntheseRapport calculer(LocalDateTime debut, LocalDateTime fin) {
		long declenchees = alerteRepository.countByDateDeclenchementBetween(debut, fin);
		long resolues = alerteRepository.countByDateResolutionBetween(debut, fin);
		int supervises = (int) equipementRepository.countByEtatNot(EtatEquipement.INACTIF);

		List<EquipementSollicite> plusSollicites =
				alerteRepository.equipementsLesPlusSollicites(debut, fin).stream()
						.limit(NOMBRE_EQUIPEMENTS_CITES)
						.map(ligne -> new EquipementSollicite((String) ligne[0], ((Number) ligne[1]).longValue()))
						.toList();

		return new SyntheseRapport(
				tauxDisponibilite(debut, fin, supervises),
				declenchees,
				resolues,
				supervises,
				plusSollicites);
	}

	/**
	 * Taux de disponibilité du parc : part du temps-équipement pendant laquelle
	 * aucune indisponibilité n'était ouverte, sur l'ensemble de la période.
	 *
	 * <p>Une alerte qui déborde de la période n'est comptée que pour sa partie
	 * incluse — sans quoi une panne de trois jours fausserait le rapport
	 * journalier qu'elle traverse.
	 */
	private BigDecimal tauxDisponibilite(LocalDateTime debut, LocalDateTime fin, int supervises) {
		long secondesPeriode = Duration.between(debut, fin).getSeconds();
		if (supervises == 0 || secondesPeriode <= 0) {
			return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
		}

		List<Alerte> indisponibilites =
				alerteRepository.chevauchantLaPeriode(TypeAnomalie.INDISPONIBILITE, debut, fin);

		long secondesIndisponibles = 0;
		for (Alerte alerte : indisponibilites) {
			LocalDateTime ouverture = alerte.getDateDeclenchement().isAfter(debut)
					? alerte.getDateDeclenchement()
					: debut;
			LocalDateTime fermeture = alerte.getDateResolution() == null || alerte.getDateResolution().isAfter(fin)
					? fin
					: alerte.getDateResolution();

			if (fermeture.isAfter(ouverture)) {
				secondesIndisponibles += Duration.between(ouverture, fermeture).getSeconds();
			}
		}

		BigDecimal tempsTotal = BigDecimal.valueOf(secondesPeriode).multiply(BigDecimal.valueOf(supervises));
		BigDecimal indisponible = BigDecimal.valueOf(secondesIndisponibles);

		return BigDecimal.valueOf(100)
				.multiply(tempsTotal.subtract(indisponible))
				.divide(tempsTotal, 2, RoundingMode.HALF_UP)
				.max(BigDecimal.ZERO);
	}
}
