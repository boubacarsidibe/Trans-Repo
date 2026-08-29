package com.bouba.backend_trans.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import com.bouba.backend_trans.alerte.dto.AlerteResponse;
import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.StatutAlerte;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;
import com.bouba.backend_trans.alerte.repository.AlerteRepository;
import com.bouba.backend_trans.equipement.entity.Equipement;

@ExtendWith(MockitoExtension.class)
class RappelAlertesCritiquesTest {

	private static final int RAPPEL_MINUTES = 60;

	@Mock
	private AlerteRepository alerteRepository;

	@Mock
	private NotificationAlerteService notificationAlerteService;

	private RappelAlertesCritiques rappelAlertesCritiques;

	@BeforeEach
	void setUp() {
		rappelAlertesCritiques = new RappelAlertesCritiques(alerteRepository, notificationAlerteService, RAPPEL_MINUTES);
	}

	private Alerte alerteCritique() {
		Equipement equipement = new Equipement();
		equipement.setId(UUID.randomUUID());
		equipement.setNom("Switch-Bloc-B");

		Alerte alerte = new Alerte();
		alerte.setId(UUID.randomUUID());
		alerte.setEquipement(equipement);
		alerte.setTypeAnomalie(TypeAnomalie.INDISPONIBILITE);
		alerte.setSeverite(Severite.CRITIQUE);
		alerte.setStatut(StatutAlerte.DECLENCHEE);
		return alerte;
	}

	@Test
	void rappeler_relance_une_critique_jamais_rappelee_declenchee_avant_le_delai() {
		Alerte alerte = alerteCritique();
		alerte.setDateDeclenchement(LocalDateTime.now().minusMinutes(RAPPEL_MINUTES + 5));
		when(alerteRepository.findByStatutAndSeverite(StatutAlerte.DECLENCHEE, Severite.CRITIQUE))
				.thenReturn(List.of(alerte));

		rappelAlertesCritiques.rappeler();

		verify(notificationAlerteService).notifier(any(AlerteResponse.class), eq(true));
		verify(alerteRepository).save(alerte);
		assertThat(alerte.getDernierRappel()).isNotNull();
	}

	@Test
	void rappeler_ne_relance_pas_une_critique_declenchee_il_y_a_moins_que_le_delai() {
		Alerte alerte = alerteCritique();
		alerte.setDateDeclenchement(LocalDateTime.now().minusMinutes(RAPPEL_MINUTES - 5));
		when(alerteRepository.findByStatutAndSeverite(StatutAlerte.DECLENCHEE, Severite.CRITIQUE))
				.thenReturn(List.of(alerte));

		rappelAlertesCritiques.rappeler();

		verify(notificationAlerteService, never()).notifier(any(), any(Boolean.class));
		verify(alerteRepository, never()).save(any());
	}

	@Test
	void rappeler_se_base_sur_le_dernier_rappel_plutot_que_le_declenchement_s_il_existe() {
		Alerte alerte = alerteCritique();
		alerte.setDateDeclenchement(LocalDateTime.now().minusMinutes(RAPPEL_MINUTES + 200));
		alerte.setDernierRappel(LocalDateTime.now().minusMinutes(RAPPEL_MINUTES - 5));
		when(alerteRepository.findByStatutAndSeverite(StatutAlerte.DECLENCHEE, Severite.CRITIQUE))
				.thenReturn(List.of(alerte));

		rappelAlertesCritiques.rappeler();

		verify(notificationAlerteService, never()).notifier(any(), any(Boolean.class));
	}

	@Test
	void rappeler_relance_de_nouveau_une_fois_le_delai_ecoule_depuis_le_dernier_rappel() {
		Alerte alerte = alerteCritique();
		alerte.setDateDeclenchement(LocalDateTime.now().minusMinutes(RAPPEL_MINUTES + 200));
		alerte.setDernierRappel(LocalDateTime.now().minusMinutes(RAPPEL_MINUTES + 5));
		when(alerteRepository.findByStatutAndSeverite(StatutAlerte.DECLENCHEE, Severite.CRITIQUE))
				.thenReturn(List.of(alerte));

		rappelAlertesCritiques.rappeler();

		verify(notificationAlerteService).notifier(any(AlerteResponse.class), eq(true));
	}
}
