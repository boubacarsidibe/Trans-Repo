package com.bouba.backend_trans.metrique.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;
import com.bouba.backend_trans.alerte.service.AlerteService;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;
import com.bouba.backend_trans.metrique.repository.MetriqueRepository;
import com.bouba.backend_trans.seuil.service.Seuil;
import com.bouba.backend_trans.seuil.service.SeuilAlerteService;

/**
 * Confronte chaque métrique ingérée au seuil configuré pour l'équipement, et
 * déclenche, élève ou résout l'alerte correspondante.
 *
 * <p>Le §11.2 exprime les seuils sous la forme « ≥ 80 % pendant 5 minutes » : un
 * dépassement n'alerte que s'il <strong>se maintient</strong> pendant la durée
 * configurée. Une pointe isolée ne réveille donc personne.
 */
@Component
public class MetriqueSeuilEvaluator {

	/**
	 * Nature de l'anomalie levée par chaque métrique. Cette correspondance est
	 * structurelle — elle ne se configure pas : seules les valeurs de seuil sont
	 * réglables par l'administrateur. Une métrique absente de cette table est
	 * purement informative et ne lève jamais d'alerte.
	 */
	private static final Map<TypeMetrique, TypeAnomalie> ANOMALIES = new EnumMap<>(TypeMetrique.class);

	static {
		ANOMALIES.put(TypeMetrique.CPU, TypeAnomalie.CPU);
		ANOMALIES.put(TypeMetrique.CHARGE_1MIN, TypeAnomalie.CPU);
		ANOMALIES.put(TypeMetrique.RAM, TypeAnomalie.RAM);
		ANOMALIES.put(TypeMetrique.SWAP, TypeAnomalie.RAM);
		ANOMALIES.put(TypeMetrique.DISQUE, TypeAnomalie.DISQUE);
		ANOMALIES.put(TypeMetrique.LATENCE, TypeAnomalie.RESEAU);
		ANOMALIES.put(TypeMetrique.TAUX_ERREUR, TypeAnomalie.RESEAU);
		ANOMALIES.put(TypeMetrique.DNS_LATENCE, TypeAnomalie.RESEAU);
		ANOMALIES.put(TypeMetrique.SERVICES_TCP_INDISPONIBLES, TypeAnomalie.INDISPONIBILITE);
		ANOMALIES.put(TypeMetrique.TEMPERATURE_MAX, TypeAnomalie.MATERIEL);
	}

	private final SeuilAlerteService seuilAlerteService;
	private final AlerteService alerteService;
	private final MetriqueRepository metriqueRepository;

	public MetriqueSeuilEvaluator(
			SeuilAlerteService seuilAlerteService,
			AlerteService alerteService,
			MetriqueRepository metriqueRepository
	) {
		this.seuilAlerteService = seuilAlerteService;
		this.alerteService = alerteService;
		this.metriqueRepository = metriqueRepository;
	}

	public void evaluer(Equipement equipement, TypeMetrique typeMetrique, BigDecimal valeur) {
		TypeAnomalie typeAnomalie = ANOMALIES.get(typeMetrique);
		if (typeAnomalie == null || valeur == null) {
			return;
		}

		Seuil seuil = seuilAlerteService.seuilEffectif(equipement.getId(), typeMetrique);
		if (seuil == null) {
			return;
		}

		if (depassementConfirme(equipement, typeMetrique, valeur, seuil.critique(), seuil.dureeSecondes())) {
			alerteService.declencherOuEleverSeverite(equipement, typeAnomalie, Severite.CRITIQUE);
		} else if (depassementConfirme(equipement, typeMetrique, valeur, seuil.avertissement(), seuil.dureeSecondes())) {
			alerteService.declencherOuEleverSeverite(equipement, typeAnomalie, Severite.AVERTISSEMENT);
		} else {
			alerteService.resoudreSiActive(equipement, typeAnomalie);
		}
	}

	/**
	 * Vrai si la mesure courante dépasse le seuil <em>et</em> que ce dépassement
	 * dure depuis au moins {@code dureeSecondes}.
	 */
	private boolean depassementConfirme(
			Equipement equipement,
			TypeMetrique typeMetrique,
			BigDecimal valeur,
			BigDecimal seuil,
			int dureeSecondes
	) {
		if (seuil == null || valeur.compareTo(seuil) < 0) {
			return false;
		}
		if (dureeSecondes <= 0) {
			return true;
		}

		LocalDateTime debutExige = LocalDateTime.now().minusSeconds(dureeSecondes);

		// Le dépassement court depuis le dernier retour sous le seuil ; s'il n'y
		// en a jamais eu, depuis la toute première mesure connue.
		LocalDateTime debutDepassement =
				metriqueRepository.dernierPassageSousSeuil(equipement.getId(), typeMetrique, seuil);
		if (debutDepassement == null) {
			debutDepassement = metriqueRepository.premiereMesure(equipement.getId(), typeMetrique);
		}

		return debutDepassement != null && !debutDepassement.isAfter(debutExige);
	}
}
