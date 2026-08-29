package com.bouba.backend_trans.disponibilite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;
import com.bouba.backend_trans.alerte.service.AlerteService;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;
import com.bouba.backend_trans.websocket.DiffusionSupervision;
import com.bouba.backend_trans.websocket.TypeEvenement;

@ExtendWith(MockitoExtension.class)
class DisponibiliteWatchdogTest {

	/** Mêmes valeurs que les valeurs par défaut d'application.properties : tolérance de 180 s. */
	private static final int INTERVALLE_COLLECTE_SECONDES = 60;
	private static final int CYCLES_TOLERES = 3;

	@Mock
	private EquipementRepository equipementRepository;

	@Mock
	private AlerteService alerteService;

	@Mock
	private DiffusionSupervision diffusionSupervision;

	private DisponibiliteWatchdog watchdog;

	@BeforeEach
	void initWatchdog() {
		watchdog = new DisponibiliteWatchdog(
				equipementRepository, alerteService, diffusionSupervision,
				INTERVALLE_COLLECTE_SECONDES, CYCLES_TOLERES);
	}

	// --- déclaration d'indisponibilité (F3/F4) ---

	@Test
	void emet_un_evenement_d_indisponibilite_quand_le_silence_depasse_les_cycles_toleres() {
		Equipement equipement = equipement("Routeur coeur", LocalDateTime.now().minusMinutes(10));
		when(equipementRepository.findByEtatNot(EtatEquipement.INACTIF)).thenReturn(List.of(equipement));

		watchdog.verifierDisponibilite();

		verify(alerteService).declencherOuEleverSeverite(equipement, TypeAnomalie.INDISPONIBILITE, Severite.CRITIQUE);
		verify(alerteService, never()).resoudreSiActive(any(), any());

		ArgumentCaptor<DisponibiliteEvenement> captor = ArgumentCaptor.forClass(DisponibiliteEvenement.class);
		verify(diffusionSupervision).publier(eq(TypeEvenement.EQUIPMENT_STATUS_CHANGED), captor.capture());
		DisponibiliteEvenement evenement = captor.getValue();
		assertThat(evenement.equipementId()).isEqualTo(equipement.getId());
		assertThat(evenement.disponible()).isFalse();
	}

	@Test
	void ne_declenche_pas_de_fausse_alerte_pour_un_equipement_qui_reprend_a_temps() {
		// dernière mesure il y a 10 s, largement sous les 180 s de tolérance
		Equipement equipement = equipement("Serveur web", LocalDateTime.now().minusSeconds(10));
		when(equipementRepository.findByEtatNot(EtatEquipement.INACTIF)).thenReturn(List.of(equipement));

		watchdog.verifierDisponibilite();

		verify(alerteService, never()).declencherOuEleverSeverite(any(), any(), any());
		verify(alerteService).resoudreSiActive(equipement, TypeAnomalie.INDISPONIBILITE);
	}

	@Test
	void ignore_un_equipement_jamais_ayant_remonte_de_mesure() {
		Equipement equipement = equipement("Poste 12", null);
		when(equipementRepository.findByEtatNot(EtatEquipement.INACTIF)).thenReturn(List.of(equipement));

		watchdog.verifierDisponibilite();

		verifyNoInteractions(alerteService, diffusionSupervision);
	}

	@Test
	void n_interroge_que_le_parc_reellement_supervise() {
		when(equipementRepository.findByEtatNot(EtatEquipement.INACTIF)).thenReturn(List.of());

		watchdog.verifierDisponibilite();

		verify(equipementRepository).findByEtatNot(EtatEquipement.INACTIF);
		verifyNoInteractions(alerteService, diffusionSupervision);
	}

	// --- émission au changement d'état seulement ---

	@Test
	void n_emet_l_evenement_qu_une_seule_fois_tant_que_l_equipement_reste_indisponible() {
		Equipement equipement = equipement("Routeur coeur", LocalDateTime.now().minusMinutes(10));
		when(equipementRepository.findByEtatNot(EtatEquipement.INACTIF)).thenReturn(List.of(equipement));

		// trois cycles de balayage successifs, l'équipement reste muet
		watchdog.verifierDisponibilite();
		watchdog.verifierDisponibilite();
		watchdog.verifierDisponibilite();

		verify(alerteService, times(3))
				.declencherOuEleverSeverite(equipement, TypeAnomalie.INDISPONIBILITE, Severite.CRITIQUE);
		verify(diffusionSupervision, times(1)).publier(eq(TypeEvenement.EQUIPMENT_STATUS_CHANGED), any());
	}

	@Test
	void emet_de_nouveau_l_evenement_quand_l_equipement_redevient_disponible_puis_se_tait_ensuite() {
		Equipement equipement = equipement("Routeur coeur", LocalDateTime.now().minusMinutes(10));
		when(equipementRepository.findByEtatNot(EtatEquipement.INACTIF)).thenReturn(List.of(equipement));

		watchdog.verifierDisponibilite(); // cycle 1 : bascule à indisponible -> événement
		watchdog.verifierDisponibilite(); // cycle 2 : toujours muet -> pas de nouvel événement

		equipement.setDerniereMesure(LocalDateTime.now()); // l'agent reprend

		watchdog.verifierDisponibilite(); // cycle 3 : bascule à disponible -> événement
		watchdog.verifierDisponibilite(); // cycle 4 : toujours joignable -> pas de nouvel événement

		ArgumentCaptor<DisponibiliteEvenement> captor = ArgumentCaptor.forClass(DisponibiliteEvenement.class);
		verify(diffusionSupervision, times(2)).publier(eq(TypeEvenement.EQUIPMENT_STATUS_CHANGED), captor.capture());

		List<DisponibiliteEvenement> evenements = captor.getAllValues();
		assertThat(evenements.get(0).disponible()).isFalse();
		assertThat(evenements.get(1).disponible()).isTrue();
	}

	// --- fixtures ---

	private Equipement equipement(String nom, LocalDateTime derniereMesure) {
		Equipement equipement = new Equipement();
		equipement.setId(UUID.randomUUID());
		equipement.setNom(nom);
		equipement.setEtat(EtatEquipement.ACTIF);
		equipement.setDerniereMesure(derniereMesure);
		return equipement;
	}
}
