package com.bouba.backend_trans.metrique.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bouba.backend_trans.config.MethodSecurityTestConfig;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;
import com.bouba.backend_trans.metrique.service.MetriqueService;

/**
 * Ingestion (clé API, contrôle manuel dans le contrôleur) et lecture de
 * l'historique (fenêtre par défaut, pagination, plafond) de
 * {@link MetriqueController}.
 */
@WebMvcTest(MetriqueController.class)
@Import(MethodSecurityTestConfig.class)
class MetriqueControllerTest {

	private static final int TAILLE_MAXIMALE = 5000;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MetriqueService metriqueService;

	// --- ingestion : correspondance agent / équipement ---

	@Test
	void ingestion_systeme_acceptee_quand_la_cle_correspond_a_l_equipement() throws Exception {
		UUID equipementId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/metrics/system")
						.with(authentication(agent(equipementId)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(corpsSysteme(equipementId)))
				.andExpect(status().isCreated());

		verify(metriqueService).ingestSystemMetrics(any());
	}

	@Test
	void ingestion_systeme_refusee_quand_la_cle_correspond_a_un_autre_equipement() throws Exception {
		UUID equipementId = UUID.randomUUID();
		UUID autreEquipementId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/metrics/system")
						.with(authentication(agent(autreEquipementId)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(corpsSysteme(equipementId)))
				.andExpect(status().isForbidden());
	}

	@Test
	void ingestion_systeme_refusee_sans_authentification() throws Exception {
		UUID equipementId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/metrics/system")
						.contentType(MediaType.APPLICATION_JSON)
						.content(corpsSysteme(equipementId)))
				.andExpect(status().isForbidden());
	}

	@Test
	void ingestion_systeme_rejette_un_corps_sans_equipement() throws Exception {
		mockMvc.perform(post("/api/v1/metrics/system")
						.with(user("admin").roles("AGENT"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"cpu_percent\": 42}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void ingestion_reseau_acceptee_quand_la_cle_correspond_a_l_equipement() throws Exception {
		UUID equipementId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/metrics/network")
						.with(authentication(agent(equipementId)))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"equipment_id\":\"" + equipementId + "\",\"bandwidth_mbps\":100}"))
				.andExpect(status().isCreated());

		verify(metriqueService).ingestNetworkMetrics(any());
	}

	@Test
	void ingestion_reseau_refusee_quand_la_cle_correspond_a_un_autre_equipement() throws Exception {
		UUID equipementId = UUID.randomUUID();
		UUID autreEquipementId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/metrics/network")
						.with(authentication(agent(autreEquipementId)))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"equipment_id\":\"" + equipementId + "\",\"bandwidth_mbps\":100}"))
				.andExpect(status().isForbidden());
	}

	// --- lecture de l'historique ---

	@Test
	void historique_sans_bornes_couvre_les_24_dernieres_heures_par_defaut() throws Exception {
		UUID equipementId = UUID.randomUUID();
		when(metriqueService.historiqueParEquipement(eq(equipementId), isNull(), any(), any()))
				.thenReturn(List.of());

		mockMvc.perform(get("/api/v1/equipments/{id}/metrics", equipementId)
						.with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isOk());

		org.mockito.ArgumentCaptor<LocalDateTime> depuisCaptor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
		verify(metriqueService).historiqueParEquipement(eq(equipementId), isNull(), depuisCaptor.capture(), any());
		LocalDateTime attendu = LocalDateTime.now().minusHours(24);
		long ecartSecondes = Math.abs(java.time.Duration.between(attendu, depuisCaptor.getValue()).getSeconds());
		org.assertj.core.api.Assertions.assertThat(ecartSecondes).isLessThan(10);
	}

	@Test
	void historique_transmet_la_page_et_la_taille_demandees() throws Exception {
		UUID equipementId = UUID.randomUUID();
		when(metriqueService.historiqueParEquipement(eq(equipementId), isNull(), any(), any()))
				.thenReturn(List.of());

		mockMvc.perform(get("/api/v1/equipments/{id}/metrics", equipementId)
						.param("page", "2")
						.param("taille", "50")
						.with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isOk());

		org.mockito.ArgumentCaptor<Pageable> pageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
		verify(metriqueService).historiqueParEquipement(eq(equipementId), isNull(), any(), pageableCaptor.capture());
		org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
		org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
	}

	@Test
	void historique_plafonne_la_taille_demandee_au_maximum_autorise() throws Exception {
		UUID equipementId = UUID.randomUUID();
		when(metriqueService.historiqueParEquipement(eq(equipementId), isNull(), any(), any()))
				.thenReturn(List.of());

		mockMvc.perform(get("/api/v1/equipments/{id}/metrics", equipementId)
						.param("taille", "999999")
						.with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isOk());

		org.mockito.ArgumentCaptor<Pageable> pageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
		verify(metriqueService).historiqueParEquipement(eq(equipementId), isNull(), any(), pageableCaptor.capture());
		org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(TAILLE_MAXIMALE);
	}

	@Test
	void historique_filtre_par_type_quand_precise() throws Exception {
		UUID equipementId = UUID.randomUUID();
		when(metriqueService.historiqueParEquipement(eq(equipementId), eq(TypeMetrique.CPU), any(), any()))
				.thenReturn(List.of());

		mockMvc.perform(get("/api/v1/equipments/{id}/metrics", equipementId)
						.param("type", "CPU")
						.with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isOk());

		verify(metriqueService).historiqueParEquipement(eq(equipementId), eq(TypeMetrique.CPU), any(), any());
	}

	@Test
	void historique_refuse_un_agent() throws Exception {
		UUID equipementId = UUID.randomUUID();

		mockMvc.perform(get("/api/v1/equipments/{id}/metrics", equipementId)
						.with(authentication(agent(equipementId))))
				.andExpect(status().isForbidden());
	}

	// --- fixtures ---

	private Authentication agent(UUID equipementId) {
		TestingAuthenticationToken token = new TestingAuthenticationToken(equipementId, null, "ROLE_AGENT");
		token.setAuthenticated(true);
		return token;
	}

	private String corpsSysteme(UUID equipementId) {
		return "{\"equipment_id\":\"" + equipementId + "\",\"cpu_percent\":42.5}";
	}
}
