package com.bouba.backend_trans.metrique.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;
import com.bouba.backend_trans.alerte.service.AlerteService;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;

/**
 * Applique les seuils par défaut du cahier des spécifications (§11.2) à chaque
 * métrique ingérée, et déclenche ou résout les alertes correspondantes.
 */
@Component
public class MetriqueSeuilEvaluator {

	private final AlerteService alerteService;

	public MetriqueSeuilEvaluator(AlerteService alerteService) {
		this.alerteService = alerteService;
	}

	public void evaluer(Equipement equipement, TypeMetrique typeMetrique, BigDecimal valeur) {
		Seuil seuil = seuilPour(typeMetrique);
		if (seuil == null || valeur == null) {
			return;
		}

		TypeAnomalie typeAnomalie = seuil.typeAnomalie();

		if (valeur.compareTo(seuil.critique()) >= 0) {
			alerteService.declencherSiAbsente(equipement, typeAnomalie, Severite.CRITIQUE);
		} else if (valeur.compareTo(seuil.avertissement()) >= 0) {
			alerteService.declencherSiAbsente(equipement, typeAnomalie, Severite.AVERTISSEMENT);
		} else {
			alerteService.resoudreSiActive(equipement, typeAnomalie);
		}
	}

	private Seuil seuilPour(TypeMetrique typeMetrique) {
		return switch (typeMetrique) {
			case CPU -> new Seuil(TypeAnomalie.CPU, new BigDecimal("80"), new BigDecimal("95"));
			case RAM -> new Seuil(TypeAnomalie.RAM, new BigDecimal("80"), new BigDecimal("95"));
			case DISQUE -> new Seuil(TypeAnomalie.DISQUE, new BigDecimal("85"), new BigDecimal("95"));
			case SWAP -> new Seuil(TypeAnomalie.RAM, new BigDecimal("60"), new BigDecimal("90"));
			case LATENCE -> new Seuil(TypeAnomalie.RESEAU, new BigDecimal("150"), new BigDecimal("400"));
			case TAUX_ERREUR -> new Seuil(TypeAnomalie.RESEAU, new BigDecimal("1"), new BigDecimal("5"));
			default -> null;
		};
	}

	private record Seuil(TypeAnomalie typeAnomalie, BigDecimal avertissement, BigDecimal critique) {
	}
}
