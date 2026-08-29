package com.bouba.backend_trans.metrique.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;
import com.bouba.backend_trans.alerte.service.AlerteService;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;
import com.bouba.backend_trans.metrique.repository.MetriqueRepository;
import com.bouba.backend_trans.seuil.service.Seuil;
import com.bouba.backend_trans.seuil.service.SeuilAlerteService;

@ExtendWith(MockitoExtension.class)
class MetriqueSeuilEvaluatorTest {

	@Mock
	private SeuilAlerteService seuilAlerteService;

	@Mock
	private AlerteService alerteService;

	@Mock
	private MetriqueRepository metriqueRepository;

	private MetriqueSeuilEvaluator evaluator;

	@BeforeEach
	void initEvaluator() {
		evaluator = new MetriqueSeuilEvaluator(seuilAlerteService, alerteService, metriqueRepository);
	}

	// --- métriques hors du périmètre des alertes ---

	@Test
	void ignore_une_metrique_absente_de_la_table_des_anomalies() {
		Equipement equipement = equipement();

		evaluator.evaluer(equipement, TypeMetrique.UPTIME, new BigDecimal("120"));

		verifyNoInteractions(seuilAlerteService, alerteService, metriqueRepository);
	}

	@Test
	void ignore_une_valeur_nulle() {
		Equipement equipement = equipement();

		evaluator.evaluer(equipement, TypeMetrique.CPU, null);

		verifyNoInteractions(seuilAlerteService, alerteService, metriqueRepository);
	}

	// --- seuil absent ---

	@Test
	void ne_declenche_rien_quand_aucun_seuil_n_est_configure() {
		Equipement equipement = equipement();
		when(seuilAlerteService.seuilEffectif(equipement.getId(), TypeMetrique.CPU)).thenReturn(null);

		evaluator.evaluer(equipement, TypeMetrique.CPU, new BigDecimal("90"));

		verifyNoInteractions(alerteService, metriqueRepository);
	}

	// --- dépassement instantané (durée exigée = 0) ---

	@Test
	void declenche_une_alerte_critique_des_le_premier_depassement_si_aucune_duree_n_est_exigee() {
		Equipement equipement = equipement();
		Seuil seuil = new Seuil(new BigDecimal("80"), new BigDecimal("95"), 0);
		when(seuilAlerteService.seuilEffectif(equipement.getId(), TypeMetrique.CPU)).thenReturn(seuil);

		evaluator.evaluer(equipement, TypeMetrique.CPU, new BigDecimal("96"));

		verify(alerteService).declencherOuEleverSeverite(equipement, TypeAnomalie.CPU, Severite.CRITIQUE);
		verifyNoInteractions(metriqueRepository);
	}

	@Test
	void declenche_une_alerte_d_avertissement_quand_seul_ce_seuil_est_franchi() {
		Equipement equipement = equipement();
		Seuil seuil = new Seuil(new BigDecimal("80"), new BigDecimal("95"), 0);
		when(seuilAlerteService.seuilEffectif(equipement.getId(), TypeMetrique.CPU)).thenReturn(seuil);

		evaluator.evaluer(equipement, TypeMetrique.CPU, new BigDecimal("85"));

		verify(alerteService).declencherOuEleverSeverite(equipement, TypeAnomalie.CPU, Severite.AVERTISSEMENT);
		verify(alerteService, never()).declencherOuEleverSeverite(any(), any(), eq(Severite.CRITIQUE));
	}

	@Test
	void resout_l_alerte_quand_la_valeur_repasse_sous_les_deux_seuils() {
		Equipement equipement = equipement();
		Seuil seuil = new Seuil(new BigDecimal("80"), new BigDecimal("95"), 0);
		when(seuilAlerteService.seuilEffectif(equipement.getId(), TypeMetrique.CPU)).thenReturn(seuil);

		evaluator.evaluer(equipement, TypeMetrique.CPU, new BigDecimal("40"));

		verify(alerteService).resoudreSiActive(equipement, TypeAnomalie.CPU);
		verify(alerteService, never()).declencherOuEleverSeverite(any(), any(), any());
	}

	@Test
	void le_seuil_est_considere_atteint_des_l_egalite_stricte() {
		Equipement equipement = equipement();
		Seuil seuil = new Seuil(new BigDecimal("80"), new BigDecimal("95"), 0);
		when(seuilAlerteService.seuilEffectif(equipement.getId(), TypeMetrique.CPU)).thenReturn(seuil);

		evaluator.evaluer(equipement, TypeMetrique.CPU, new BigDecimal("80"));

		verify(alerteService).declencherOuEleverSeverite(equipement, TypeAnomalie.CPU, Severite.AVERTISSEMENT);
	}

	// --- maintien du dépassement dans la durée (§11.2 : « pendant 5 minutes ») ---

	@Test
	void n_alerte_pas_sur_une_pointe_isolee_qui_ne_tient_pas_la_duree_exigee() {
		Equipement equipement = equipement();
		BigDecimal avertissement = new BigDecimal("80");
		BigDecimal critique = new BigDecimal("95");
		Seuil seuil = new Seuil(avertissement, critique, 300);
		when(seuilAlerteService.seuilEffectif(equipement.getId(), TypeMetrique.CPU)).thenReturn(seuil);
		// la métrique n'est repassée sous les deux seuils qu'il y a 10 secondes : le
		// dépassement ne dure pas encore depuis les 5 minutes exigées.
		LocalDateTime ilYA10Secondes = LocalDateTime.now().minusSeconds(10);
		when(metriqueRepository.dernierPassageSousSeuil(equipement.getId(), TypeMetrique.CPU, critique))
				.thenReturn(ilYA10Secondes);
		when(metriqueRepository.dernierPassageSousSeuil(equipement.getId(), TypeMetrique.CPU, avertissement))
				.thenReturn(ilYA10Secondes);

		evaluator.evaluer(equipement, TypeMetrique.CPU, new BigDecimal("96"));

		verify(alerteService, never()).declencherOuEleverSeverite(any(), any(), any());
		verify(alerteService).resoudreSiActive(equipement, TypeAnomalie.CPU);
	}

	@Test
	void confirme_le_depassement_critique_quand_il_dure_depuis_au_moins_la_duree_exigee() {
		Equipement equipement = equipement();
		BigDecimal avertissement = new BigDecimal("80");
		BigDecimal critique = new BigDecimal("95");
		Seuil seuil = new Seuil(avertissement, critique, 300);
		when(seuilAlerteService.seuilEffectif(equipement.getId(), TypeMetrique.CPU)).thenReturn(seuil);
		when(metriqueRepository.dernierPassageSousSeuil(equipement.getId(), TypeMetrique.CPU, critique))
				.thenReturn(LocalDateTime.now().minusMinutes(10));

		evaluator.evaluer(equipement, TypeMetrique.CPU, new BigDecimal("97"));

		verify(alerteService).declencherOuEleverSeverite(equipement, TypeAnomalie.CPU, Severite.CRITIQUE);
		verify(metriqueRepository, never()).premiereMesure(any(), any());
	}

	@Test
	void se_base_sur_la_premiere_mesure_connue_quand_la_metrique_n_est_jamais_repassee_sous_le_seuil() {
		Equipement equipement = equipement();
		BigDecimal avertissement = new BigDecimal("80");
		BigDecimal critique = new BigDecimal("95");
		Seuil seuil = new Seuil(avertissement, critique, 300);
		when(seuilAlerteService.seuilEffectif(equipement.getId(), TypeMetrique.CPU)).thenReturn(seuil);
		when(metriqueRepository.dernierPassageSousSeuil(equipement.getId(), TypeMetrique.CPU, critique))
				.thenReturn(null);
		when(metriqueRepository.premiereMesure(equipement.getId(), TypeMetrique.CPU))
				.thenReturn(LocalDateTime.now().minusMinutes(10));

		evaluator.evaluer(equipement, TypeMetrique.CPU, new BigDecimal("97"));

		verify(alerteService).declencherOuEleverSeverite(equipement, TypeAnomalie.CPU, Severite.CRITIQUE);
	}

	@Test
	void n_alerte_pas_quand_aucune_mesure_n_existe_encore_pour_fixer_le_debut_du_depassement() {
		Equipement equipement = equipement();
		BigDecimal avertissement = new BigDecimal("80");
		BigDecimal critique = new BigDecimal("95");
		Seuil seuil = new Seuil(avertissement, critique, 300);
		when(seuilAlerteService.seuilEffectif(equipement.getId(), TypeMetrique.CPU)).thenReturn(seuil);
		when(metriqueRepository.dernierPassageSousSeuil(equipement.getId(), TypeMetrique.CPU, critique))
				.thenReturn(null);
		when(metriqueRepository.dernierPassageSousSeuil(equipement.getId(), TypeMetrique.CPU, avertissement))
				.thenReturn(null);
		when(metriqueRepository.premiereMesure(equipement.getId(), TypeMetrique.CPU))
				.thenReturn(null);

		evaluator.evaluer(equipement, TypeMetrique.CPU, new BigDecimal("97"));

		verify(alerteService, never()).declencherOuEleverSeverite(any(), any(), any());
		verify(alerteService).resoudreSiActive(equipement, TypeAnomalie.CPU);
	}

	@Test
	void confirme_un_depassement_d_avertissement_maintenu_quand_la_valeur_n_atteint_pas_le_seuil_critique() {
		Equipement equipement = equipement();
		BigDecimal avertissement = new BigDecimal("80");
		BigDecimal critique = new BigDecimal("95");
		Seuil seuil = new Seuil(avertissement, critique, 300);
		when(seuilAlerteService.seuilEffectif(equipement.getId(), TypeMetrique.CPU)).thenReturn(seuil);
		when(metriqueRepository.dernierPassageSousSeuil(equipement.getId(), TypeMetrique.CPU, avertissement))
				.thenReturn(LocalDateTime.now().minusMinutes(10));

		evaluator.evaluer(equipement, TypeMetrique.CPU, new BigDecimal("85"));

		verify(alerteService).declencherOuEleverSeverite(equipement, TypeAnomalie.CPU, Severite.AVERTISSEMENT);
		verify(alerteService, never()).declencherOuEleverSeverite(any(), any(), eq(Severite.CRITIQUE));
	}

	// --- correspondance métrique → type d'anomalie ---

	@Test
	void associe_une_saturation_de_swap_a_une_anomalie_ram() {
		Equipement equipement = equipement();
		Seuil seuil = new Seuil(new BigDecimal("60"), new BigDecimal("90"), 0);
		when(seuilAlerteService.seuilEffectif(equipement.getId(), TypeMetrique.SWAP)).thenReturn(seuil);

		evaluator.evaluer(equipement, TypeMetrique.SWAP, new BigDecimal("95"));

		verify(alerteService).declencherOuEleverSeverite(equipement, TypeAnomalie.RAM, Severite.CRITIQUE);
	}

	// --- fixtures ---

	private Equipement equipement() {
		Equipement equipement = new Equipement();
		equipement.setId(UUID.randomUUID());
		equipement.setNom("Serveur applicatif");
		return equipement;
	}
}
