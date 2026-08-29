package com.bouba.backend_trans.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Point d'entrée unique de la diffusion temps réel : publication d'un
 * {@link EvenementSupervision} en transaction, puis diffusion effective sur
 * le bon canal une fois le commit passé (§8.3).
 */
class DiffusionSupervisionTest {

	private final SupervisionWebSocketHandler handler = mock(SupervisionWebSocketHandler.class);
	private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
	private final ObjectMapper objectMapper = JsonMapper.builder().build();
	private final DiffusionSupervision diffusion = new DiffusionSupervision(handler, objectMapper, publisher);

	@Test
	void publier_encapsule_le_type_et_la_charge_utile_dans_un_evenement() {
		diffusion.publier(TypeEvenement.ALERT_CREATED, Map.of("id", "42"));

		ArgumentCaptor<EvenementSupervision> capture = ArgumentCaptor.forClass(EvenementSupervision.class);
		verify(publisher).publishEvent(capture.capture());
		assertThat(capture.getValue().type()).isEqualTo(TypeEvenement.ALERT_CREATED);
		assertThat(capture.getValue().payload()).isEqualTo(Map.of("id", "42"));
	}

	@Test
	void sur_evenement_diffuse_le_message_serialise_sur_le_canal_du_type() {
		EvenementSupervision evenement = EvenementSupervision.de(TypeEvenement.METRIC_UPDATE, Map.of("cpu", 42));

		diffusion.surEvenement(evenement);

		ArgumentCaptor<String> messageCapture = ArgumentCaptor.forClass(String.class);
		verify(handler).diffuser(org.mockito.ArgumentMatchers.eq(CanalSupervision.METRICS), messageCapture.capture());
		assertThat(messageCapture.getValue()).contains("\"type\":\"metric_update\"");
	}

	@Test
	void sur_evenement_route_vers_le_canal_alerts_pour_un_type_d_alerte() {
		EvenementSupervision evenement = EvenementSupervision.de(TypeEvenement.ALERT_RESOLVED, Map.of());

		diffusion.surEvenement(evenement);

		verify(handler).diffuser(org.mockito.ArgumentMatchers.eq(CanalSupervision.ALERTS), any());
	}

	@Test
	void un_echec_de_serialisation_n_interrompt_pas_le_traitement_metier() {
		ObjectMapper objectMapperDefaillant = mock(ObjectMapper.class);
		when(objectMapperDefaillant.writeValueAsString(any()))
				.thenThrow(JacksonIOException.construct(new IOException("panne d'écriture")));
		DiffusionSupervision diffusionDefaillante =
				new DiffusionSupervision(handler, objectMapperDefaillant, publisher);
		EvenementSupervision evenement = EvenementSupervision.de(TypeEvenement.METRIC_UPDATE, Map.of());

		assertThatCode(() -> diffusionDefaillante.surEvenement(evenement)).doesNotThrowAnyException();
		verify(handler, never()).diffuser(any(), any());
	}
}
