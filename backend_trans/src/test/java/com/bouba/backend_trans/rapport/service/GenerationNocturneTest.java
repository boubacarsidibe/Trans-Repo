package com.bouba.backend_trans.rapport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bouba.backend_trans.rapport.dto.RapportGenerateRequest;
import com.bouba.backend_trans.rapport.entity.Rapport;
import com.bouba.backend_trans.rapport.entity.TypeRapport;

/**
 * Génération automatique nocturne du rapport journalier de la veille (F8).
 */
class GenerationNocturneTest {

	private final RapportService rapportService = mock(RapportService.class);
	private final GenerationNocturne generationNocturne = new GenerationNocturne(rapportService);

	@Test
	void genere_un_rapport_journalier_couvrant_toute_la_journee_de_la_veille() {
		Rapport rapportGenere = new Rapport();
		rapportGenere.setId(UUID.randomUUID());
		when(rapportService.generate(any())).thenReturn(rapportGenere);

		generationNocturne.genererRapportDeLaVeille();

		ArgumentCaptor<RapportGenerateRequest> capture = ArgumentCaptor.forClass(RapportGenerateRequest.class);
		verify(rapportService).generate(capture.capture());

		LocalDate veille = LocalDate.now().minusDays(1);
		RapportGenerateRequest demande = capture.getValue();
		assertThat(demande.getTypeRapport()).isEqualTo(TypeRapport.JOURNALIER);
		assertThat(demande.getPeriodeDebut()).isEqualTo(veille.atStartOfDay());
		assertThat(demande.getPeriodeFin()).isEqualTo(veille.plusDays(1).atStartOfDay());
	}

	@Test
	void n_interrompt_pas_l_appelant_si_la_generation_echoue() {
		when(rapportService.generate(any())).thenThrow(new IllegalStateException("échec de génération"));

		generationNocturne.genererRapportDeLaVeille();

		verify(rapportService).generate(any());
	}
}
