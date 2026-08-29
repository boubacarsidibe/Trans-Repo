package com.bouba.backend_trans.rapport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;
import com.bouba.backend_trans.alerte.repository.AlerteRepository;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.TypeEquipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;

/**
 * Calcul des indicateurs d'un rapport (F8) : disponibilité du parc,
 * alertes déclenchées/résolues et équipements les plus sollicités,
 * sur une période donnée.
 */
class RapportCalculateurTest {

	private final AlerteRepository alerteRepository = mock(AlerteRepository.class);
	private final EquipementRepository equipementRepository = mock(EquipementRepository.class);
	private final RapportCalculateur calculateur = new RapportCalculateur(alerteRepository, equipementRepository);

	private LocalDateTime debut;
	private LocalDateTime fin;

	@BeforeEach
	void periodeParDefaut() {
		debut = LocalDateTime.of(2026, 8, 1, 0, 0);
		fin = LocalDateTime.of(2026, 8, 2, 0, 0);
	}

	@Test
	void compte_les_alertes_declenchees_et_resolues_sur_la_periode() {
		when(alerteRepository.countByDateDeclenchementBetween(debut, fin)).thenReturn(7L);
		when(alerteRepository.countByDateResolutionBetween(debut, fin)).thenReturn(5L);
		when(equipementRepository.countByEtatNot(EtatEquipement.INACTIF)).thenReturn(10L);
		when(alerteRepository.equipementsLesPlusSollicites(debut, fin)).thenReturn(List.of());
		when(alerteRepository.chevauchantLaPeriode(eq(TypeAnomalie.INDISPONIBILITE), any(), any()))
				.thenReturn(List.of());

		SyntheseRapport synthese = calculateur.calculer(debut, fin);

		assertThat(synthese.alertesDeclenchees()).isEqualTo(7L);
		assertThat(synthese.alertesResolues()).isEqualTo(5L);
		assertThat(synthese.equipementsSupervises()).isEqualTo(10);
	}

	@Test
	void limite_les_equipements_les_plus_sollicites_a_cinq() {
		sansIndisponibilite();
		List<Object[]> lignes = List.of(
				ligne("routeur-1", 9L), ligne("routeur-2", 8L), ligne("routeur-3", 7L),
				ligne("routeur-4", 6L), ligne("routeur-5", 5L), ligne("routeur-6", 4L));
		when(alerteRepository.equipementsLesPlusSollicites(debut, fin)).thenReturn(lignes);

		SyntheseRapport synthese = calculateur.calculer(debut, fin);

		assertThat(synthese.equipementsLesPlusSollicites()).hasSize(5);
		assertThat(synthese.equipementsLesPlusSollicites().get(0).nom()).isEqualTo("routeur-1");
		assertThat(synthese.equipementsLesPlusSollicites().get(0).alertes()).isEqualTo(9L);
	}

	// --- taux de disponibilité ---

	@Test
	void taux_de_disponibilite_de_cent_pourcent_sans_equipement_supervise() {
		when(equipementRepository.countByEtatNot(EtatEquipement.INACTIF)).thenReturn(0L);
		when(alerteRepository.equipementsLesPlusSollicites(debut, fin)).thenReturn(List.of());

		SyntheseRapport synthese = calculateur.calculer(debut, fin);

		assertThat(synthese.tauxDisponibilite()).isEqualByComparingTo("100.00");
	}

	@Test
	void taux_de_disponibilite_de_cent_pourcent_sans_aucune_indisponibilite() {
		when(equipementRepository.countByEtatNot(EtatEquipement.INACTIF)).thenReturn(2L);
		when(alerteRepository.equipementsLesPlusSollicites(debut, fin)).thenReturn(List.of());
		when(alerteRepository.chevauchantLaPeriode(eq(TypeAnomalie.INDISPONIBILITE), any(), any()))
				.thenReturn(List.of());

		SyntheseRapport synthese = calculateur.calculer(debut, fin);

		assertThat(synthese.tauxDisponibilite()).isEqualByComparingTo("100.00");
	}

	@Test
	void deduit_le_temps_indisponible_d_une_panne_entierement_dans_la_periode() {
		when(equipementRepository.countByEtatNot(EtatEquipement.INACTIF)).thenReturn(1L);
		when(alerteRepository.equipementsLesPlusSollicites(debut, fin)).thenReturn(List.of());
		// Panne de 6h sur une période de 24h pour un seul équipement -> 75% de disponibilité.
		Alerte panne = indisponibilite(debut.plusHours(3), debut.plusHours(9));
		when(alerteRepository.chevauchantLaPeriode(eq(TypeAnomalie.INDISPONIBILITE), any(), any()))
				.thenReturn(List.of(panne));

		SyntheseRapport synthese = calculateur.calculer(debut, fin);

		assertThat(synthese.tauxDisponibilite()).isEqualByComparingTo("75.00");
	}

	@Test
	void ne_compte_que_la_partie_d_une_panne_incluse_dans_la_periode() {
		when(equipementRepository.countByEtatNot(EtatEquipement.INACTIF)).thenReturn(1L);
		when(alerteRepository.equipementsLesPlusSollicites(debut, fin)).thenReturn(List.of());
		// Panne commencée avant le début de la période et toujours active à la fin :
		// seules les 24h de la période comptent comme indisponibles, pas plus.
		Alerte panne = indisponibilite(debut.minusDays(3), null);
		when(alerteRepository.chevauchantLaPeriode(eq(TypeAnomalie.INDISPONIBILITE), any(), any()))
				.thenReturn(List.of(panne));

		SyntheseRapport synthese = calculateur.calculer(debut, fin);

		assertThat(synthese.tauxDisponibilite()).isEqualByComparingTo("0.00");
	}

	private void sansIndisponibilite() {
		when(equipementRepository.countByEtatNot(EtatEquipement.INACTIF)).thenReturn(1L);
		when(alerteRepository.chevauchantLaPeriode(eq(TypeAnomalie.INDISPONIBILITE), any(), any()))
				.thenReturn(List.of());
	}

	private Object[] ligne(String nom, long alertes) {
		return new Object[] { nom, alertes };
	}

	private Alerte indisponibilite(LocalDateTime declenchement, LocalDateTime resolution) {
		Equipement equipement = new Equipement();
		equipement.setId(UUID.randomUUID());
		equipement.setNom("Routeur coeur");
		equipement.setAdresseIp("10.0.0.1");
		equipement.setType(TypeEquipement.ROUTEUR);
		equipement.setEtat(EtatEquipement.ACTIF);

		Alerte alerte = new Alerte();
		alerte.setId(UUID.randomUUID());
		alerte.setEquipement(equipement);
		alerte.setTypeAnomalie(TypeAnomalie.INDISPONIBILITE);
		alerte.setDateDeclenchement(declenchement);
		alerte.setDateResolution(resolution);
		return alerte;
	}
}
