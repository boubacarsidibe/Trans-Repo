package com.bouba.backend_trans.collecteur;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.bouba.backend_trans.collecteur.entity.Collecteur;
import com.bouba.backend_trans.collecteur.repository.CollecteurRepository;
import com.bouba.backend_trans.websocket.DiffusionSupervision;
import com.bouba.backend_trans.websocket.TypeEvenement;

class CollecteurWatchdogTest {

	private static final int INTERVALLE_SECONDES = 60;
	private static final int CYCLES_TOLERES = 3;

	private final CollecteurRepository collecteurRepository = mock(CollecteurRepository.class);
	private final DiffusionSupervision diffusionSupervision = mock(DiffusionSupervision.class);
	private final CollecteurWatchdog watchdog = new CollecteurWatchdog(
			collecteurRepository, diffusionSupervision, INTERVALLE_SECONDES, CYCLES_TOLERES);

	@Test
	void ne_diffuse_rien_si_aucune_instance_ne_s_est_jamais_declaree_active() {
		when(collecteurRepository.findFirstByActifTrueOrderByDernierHeartbeatDesc()).thenReturn(Optional.empty());

		watchdog.verifierCollecteurActif();

		verify(diffusionSupervision, never()).publier(any(), any());
	}

	@Test
	void diffuse_indisponible_quand_le_heartbeat_est_trop_ancien() {
		Collecteur collecteur = collecteur("primaire", LocalDateTime.now().minusSeconds(1000));
		when(collecteurRepository.findFirstByActifTrueOrderByDernierHeartbeatDesc())
				.thenReturn(Optional.of(collecteur));

		watchdog.verifierCollecteurActif();

		verify(diffusionSupervision).publier(
				eq(TypeEvenement.COLLECTOR_STATUS_CHANGED),
				eq(new CollecteurEvenement("primaire", false, collecteur.getDernierHeartbeat())));
	}

	@Test
	void ne_diffuse_pas_quand_le_heartbeat_est_recent() {
		Collecteur collecteur = collecteur("primaire", LocalDateTime.now());
		when(collecteurRepository.findFirstByActifTrueOrderByDernierHeartbeatDesc())
				.thenReturn(Optional.of(collecteur));

		watchdog.verifierCollecteurActif();

		verify(diffusionSupervision).publier(
				eq(TypeEvenement.COLLECTOR_STATUS_CHANGED),
				eq(new CollecteurEvenement("primaire", true, collecteur.getDernierHeartbeat())));
	}

	@Test
	void n_emet_qu_un_seul_evenement_tant_que_l_etat_ne_change_pas() {
		Collecteur collecteur = collecteur("primaire", LocalDateTime.now());
		when(collecteurRepository.findFirstByActifTrueOrderByDernierHeartbeatDesc())
				.thenReturn(Optional.of(collecteur));

		watchdog.verifierCollecteurActif();
		watchdog.verifierCollecteurActif();
		watchdog.verifierCollecteurActif();

		verify(diffusionSupervision, times(1)).publier(eq(TypeEvenement.COLLECTOR_STATUS_CHANGED), any());
	}

	@Test
	void reemet_un_evenement_quand_l_etat_change() {
		Collecteur disponible = collecteur("primaire", LocalDateTime.now());
		when(collecteurRepository.findFirstByActifTrueOrderByDernierHeartbeatDesc())
				.thenReturn(Optional.of(disponible));
		watchdog.verifierCollecteurActif();

		Collecteur muet = collecteur("primaire", LocalDateTime.now().minusSeconds(1000));
		when(collecteurRepository.findFirstByActifTrueOrderByDernierHeartbeatDesc())
				.thenReturn(Optional.of(muet));
		watchdog.verifierCollecteurActif();

		verify(diffusionSupervision, times(2)).publier(eq(TypeEvenement.COLLECTOR_STATUS_CHANGED), any());
	}

	private Collecteur collecteur(String id, LocalDateTime dernierHeartbeat) {
		Collecteur collecteur = new Collecteur();
		collecteur.setCollecteurId(id);
		collecteur.setActif(true);
		collecteur.setDernierHeartbeat(dernierHeartbeat);
		return collecteur;
	}
}
