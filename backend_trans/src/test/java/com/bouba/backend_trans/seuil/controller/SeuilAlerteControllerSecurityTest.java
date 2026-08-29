package com.bouba.backend_trans.seuil.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bouba.backend_trans.config.MethodSecurityTestConfig;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;
import com.bouba.backend_trans.seuil.entity.SeuilAlerte;
import com.bouba.backend_trans.seuil.service.SeuilAlerteService;

/**
 * Vérifie la matrice RBAC (§4.4) de {@link SeuilAlerteController} : lecture
 * pour administrateur et technicien (l'observateur n'a ici aucun accès,
 * contrairement aux autres modules), écriture réservée au seul
 * administrateur.
 */
@WebMvcTest(SeuilAlerteController.class)
@Import(MethodSecurityTestConfig.class)
class SeuilAlerteControllerSecurityTest {

	private static final String CORPS_VALIDE = """
			{"typeMetrique":"CPU","avertissement":80,"critique":95,"dureeSecondes":300}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SeuilAlerteService seuilAlerteService;

	@Test
	void technicien_peut_lister_les_seuils() throws Exception {
		when(seuilAlerteService.findAll()).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/thresholds").with(user("tech").roles("TECHNICIEN")))
				.andExpect(status().isOk());
	}

	@Test
	void administrateur_peut_creer_un_seuil() throws Exception {
		when(seuilAlerteService.create(any())).thenReturn(seuil());

		mockMvc.perform(post("/api/v1/thresholds")
						.with(user("admin").roles("ADMINISTRATEUR"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(CORPS_VALIDE))
				.andExpect(status().isCreated());
	}

	@Test
	void observateur_ne_peut_pas_lister_les_seuils() throws Exception {
		mockMvc.perform(get("/api/v1/thresholds").with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isForbidden());
	}

	@Test
	void technicien_ne_peut_pas_creer_un_seuil() throws Exception {
		mockMvc.perform(post("/api/v1/thresholds")
						.with(user("tech").roles("TECHNICIEN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(CORPS_VALIDE))
				.andExpect(status().isForbidden());
	}

	@Test
	void technicien_ne_peut_pas_supprimer_un_seuil() throws Exception {
		mockMvc.perform(delete("/api/v1/thresholds/{id}", UUID.randomUUID())
						.with(user("tech").roles("TECHNICIEN")))
				.andExpect(status().isForbidden());
	}

	@Test
	void observateur_ne_peut_pas_creer_un_seuil() throws Exception {
		mockMvc.perform(post("/api/v1/thresholds")
						.with(user("obs").roles("OBSERVATEUR"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(CORPS_VALIDE))
				.andExpect(status().isForbidden());
	}

	private SeuilAlerte seuil() {
		SeuilAlerte seuil = new SeuilAlerte();
		seuil.setId(UUID.randomUUID());
		seuil.setTypeMetrique(TypeMetrique.CPU);
		seuil.setAvertissement(BigDecimal.valueOf(80));
		seuil.setCritique(BigDecimal.valueOf(95));
		seuil.setDureeSecondes(300);
		return seuil;
	}
}
