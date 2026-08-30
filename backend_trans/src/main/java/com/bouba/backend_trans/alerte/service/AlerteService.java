package com.bouba.backend_trans.alerte.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.alerte.dto.AlerteResponse;
import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.StatutAlerte;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;
import com.bouba.backend_trans.alerte.repository.AlerteRepository;
import com.bouba.backend_trans.audit.Auditable;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.websocket.DiffusionSupervision;
import com.bouba.backend_trans.websocket.TypeEvenement;

@Service
public class AlerteService {

	/** Garde-fou contre une chaîne de dépendance circulaire. */
	private static final int PROFONDEUR_MAX_DEPENDANCE = 10;

	private final AlerteRepository alerteRepository;
	private final DiffusionSupervision diffusionSupervision;

	public AlerteService(AlerteRepository alerteRepository, DiffusionSupervision diffusionSupervision) {
		this.alerteRepository = alerteRepository;
		this.diffusionSupervision = diffusionSupervision;
	}

	@Transactional(readOnly = true)
	public List<Alerte> findAll() {
		return alerteRepository.findAll();
	}

	@Transactional(readOnly = true)
	public List<Alerte> findByStatut(StatutAlerte statut) {
		return alerteRepository.findByStatutOrderByDateDeclenchementDesc(statut);
	}

	/** Recherche paginée et filtrable des alertes (§7.9). */
	@Transactional(readOnly = true)
	public List<Alerte> rechercher(StatutAlerte statut, Severite severite, Pageable pageable) {
		if (statut != null && severite != null) {
			return alerteRepository.findByStatutAndSeverite(statut, severite, pageable);
		}
		if (statut != null) {
			return alerteRepository.findByStatut(statut, pageable);
		}
		if (severite != null) {
			return alerteRepository.findBySeverite(severite, pageable);
		}
		return alerteRepository.findAllBy(pageable);
	}

	@Transactional(readOnly = true)
	public Alerte findById(UUID id) {
		return alerteRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Alerte introuvable."));
	}

	/**
	 * Crée l'alerte si aucune de même nature n'est active pour cet équipement
	 * (anti-répétition, §11.4), et se contente d'élever la sévérité si une
	 * alerte moins grave court déjà.
	 *
	 * <p>Sans cette élévation, un processeur passé de 82 % à 98 % resterait
	 * affiché en « avertissement » aussi longtemps que l'alerte initiale reste
	 * ouverte — le panneau mentirait sur la gravité réelle.
	 */
	@Transactional
	public void declencherOuEleverSeverite(Equipement equipement, TypeAnomalie typeAnomalie, Severite severite) {
		// Règle F2 : une maintenance déclarée éteint les alertes de disponibilité
		// de cet équipement, et elles seules — une saturation disque pendant une
		// intervention reste une information utile.
		if (typeAnomalie == TypeAnomalie.INDISPONIBILITE
				&& equipement.getEtat() == EtatEquipement.EN_MAINTENANCE) {
			return;
		}

		// Un équipement injoignable parce que le commutateur qui le dessert est
		// tombé n'est pas une panne de plus : c'est la même panne. On n'alerte
		// que sur la cause.
		if (typeAnomalie == TypeAnomalie.INDISPONIBILITE && dependDunParentIndisponible(equipement)) {
			return;
		}

		Optional<Alerte> active = alerteRepository.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
				equipement.getId(), typeAnomalie, StatutAlerte.RESOLUE);

		if (active.isPresent()) {
			Alerte alerte = active.get();
			if (alerte.getSeverite().compareTo(severite) < 0) {
				alerte.setSeverite(severite);
				alerteRepository.save(alerte);
				diffuser(TypeEvenement.ALERT_UPDATED, alerte);
			}
			return;
		}

		Alerte alerte = new Alerte();
		alerte.setEquipement(equipement);
		alerte.setTypeAnomalie(typeAnomalie);
		alerte.setSeverite(severite);
		alerte.setStatut(StatutAlerte.DECLENCHEE);
		alerteRepository.save(alerte);

		diffuser(TypeEvenement.ALERT_CREATED, alerte);
	}

	/**
	 * Clôture automatiquement l'alerte active pour ce couple équipement/anomalie
	 * lorsque la métrique repasse sous le seuil.
	 */
	@Transactional
	public void resoudreSiActive(Equipement equipement, TypeAnomalie typeAnomalie) {
		alerteRepository
				.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
						equipement.getId(), typeAnomalie, StatutAlerte.RESOLUE)
				.ifPresent(alerte -> {
					alerte.setStatut(StatutAlerte.RESOLUE);
					alerte.setDateResolution(LocalDateTime.now());
					alerteRepository.save(alerte);
					diffuser(TypeEvenement.ALERT_RESOLVED, alerte);
				});
	}

	@Transactional
	@Auditable("PRISE_EN_COMPTE_ALERTE")
	public Alerte acknowledge(UUID id, AppUser utilisateur) {
		Alerte alerte = findById(id);
		alerte.setStatut(StatutAlerte.PRISE_EN_COMPTE);
		alerte.setUtilisateurPriseEnCharge(utilisateur);
		alerteRepository.save(alerte);

		diffuser(TypeEvenement.ALERT_ACKNOWLEDGED, alerte);
		return alerte;
	}

	@Transactional
	@Auditable("RESOLUTION_ALERTE")
	public Alerte resolve(UUID id) {
		Alerte alerte = findById(id);
		alerte.setStatut(StatutAlerte.RESOLUE);
		alerte.setDateResolution(LocalDateTime.now());
		alerteRepository.save(alerte);

		diffuser(TypeEvenement.ALERT_RESOLVED, alerte);
		return alerte;
	}

	/**
	 * Remonte la chaîne de dépendance à la recherche d'un équipement déjà déclaré
	 * indisponible.
	 *
	 * <p>La profondeur est bornée : une boucle de dépendance introduite par
	 * erreur en configuration ne doit pas faire tourner l'ingestion à l'infini,
	 * même si {@code EquipementService} refuse déjà de la créer.
	 */
	private boolean dependDunParentIndisponible(Equipement equipement) {
		Equipement parent = equipement.getDependDe();

		for (int profondeur = 0; parent != null && profondeur < PROFONDEUR_MAX_DEPENDANCE; profondeur++) {
			boolean parentEnPanne = alerteRepository
					.findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
							parent.getId(), TypeAnomalie.INDISPONIBILITE, StatutAlerte.RESOLUE)
					.isPresent();
			if (parentEnPanne) {
				return true;
			}
			parent = parent.getDependDe();
		}

		return false;
	}

	/**
	 * La projection est construite ici, dans la transaction : la diffusion a lieu
	 * après le commit, quand les relations paresseuses ne sont plus chargeables.
	 */
	private void diffuser(TypeEvenement type, Alerte alerte) {
		diffusionSupervision.publier(type, AlerteResponse.fromEntity(alerte));
	}
}
