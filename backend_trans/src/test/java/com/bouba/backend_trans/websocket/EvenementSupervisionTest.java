package com.bouba.backend_trans.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Le contrat §8.3 nomme les événements en minuscules ({@code metric_update}) :
 * la console s'appuie dessus pour aiguiller les messages reçus.
 */
class EvenementSupervisionTest {

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	@Test
	void serialise_le_type_dans_la_forme_attendue_par_la_console() {
		String json = objectMapper.writeValueAsString(
				EvenementSupervision.de(TypeEvenement.METRIC_UPDATE, Map.of("cle", "valeur")));

		assertThat(json).contains("\"type\":\"metric_update\"");
		assertThat(json).contains("\"payload\"");
		assertThat(json).contains("\"horodatage\"");
	}

	@ParameterizedTest
	@EnumSource(TypeEvenement.class)
	void chaque_type_porte_un_code_en_minuscules_et_un_canal(TypeEvenement type) {
		assertThat(type.getCode()).isEqualTo(type.getCode().toLowerCase());
		assertThat(type.getCanal()).isNotNull();
	}

	@Test
	void les_alertes_et_les_metriques_ne_partagent_pas_le_meme_canal() {
		assertThat(TypeEvenement.ALERT_CREATED.getCanal()).isEqualTo(CanalSupervision.ALERTS);
		assertThat(TypeEvenement.METRIC_UPDATE.getCanal()).isEqualTo(CanalSupervision.METRICS);
		assertThat(TypeEvenement.EQUIPMENT_STATUS_CHANGED.getCanal()).isEqualTo(CanalSupervision.STATUS);
	}
}
