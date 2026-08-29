package com.bouba.backend_trans.rapport.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import com.bouba.backend_trans.rapport.entity.Rapport;
import com.bouba.backend_trans.rapport.entity.TypeRapport;
import com.bouba.backend_trans.rapport.service.RapportService;
import com.bouba.backend_trans.rapport.service.SyntheseRapport;

/**
 * Vérifie la matrice RBAC (§4.4) de {@link RapportController} : lecture
 * ouverte aux trois rôles, génération réservée à administrateur et
 * technicien.
 */
@WebMvcTest(RapportController.class)
@Import(MethodSecurityTestConfig.class)
class RapportControllerSecurityTest {

	private static final String CORPS_VALIDE = """
			{"typeRapport":"JOURNALIER"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RapportService rapportService;

	@Test
	void observateur_peut_lister_les_rapports() throws Exception {
		when(rapportService.findAll()).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/reports").with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isOk());
	}

	@Test
	void technicien_peut_generer_un_rapport() throws Exception {
		Rapport rapport = rapport();
		when(rapportService.generate(any())).thenReturn(rapport);
		when(rapportService.synthese(rapport))
				.thenReturn(new SyntheseRapport(BigDecimal.valueOf(99.5), 3, 2, 10, List.of()));

		mockMvc.perform(post("/api/v1/reports/generate")
						.with(user("tech").roles("TECHNICIEN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(CORPS_VALIDE))
				.andExpect(status().isCreated());
	}

	@Test
	void observateur_ne_peut_pas_generer_un_rapport() throws Exception {
		mockMvc.perform(post("/api/v1/reports/generate")
						.with(user("obs").roles("OBSERVATEUR"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(CORPS_VALIDE))
				.andExpect(status().isForbidden());
	}

	private Rapport rapport() {
		Rapport rapport = new Rapport();
		rapport.setId(UUID.randomUUID());
		rapport.setTypeRapport(TypeRapport.JOURNALIER);
		return rapport;
	}
}
