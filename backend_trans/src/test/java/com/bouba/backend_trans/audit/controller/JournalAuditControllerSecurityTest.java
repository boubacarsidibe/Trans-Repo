package com.bouba.backend_trans.audit.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bouba.backend_trans.audit.service.JournalAuditService;
import com.bouba.backend_trans.config.MethodSecurityTestConfig;

/**
 * Vérifie la matrice RBAC (§4.4) de {@link JournalAuditController} : le
 * journal d'audit n'est accessible qu'à l'administrateur.
 */
@WebMvcTest(JournalAuditController.class)
@Import(MethodSecurityTestConfig.class)
class JournalAuditControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JournalAuditService journalAuditService;

	@Test
	void administrateur_peut_consulter_le_journal_d_audit() throws Exception {
		when(journalAuditService.findAll(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/audit-log").with(user("admin").roles("ADMINISTRATEUR")))
				.andExpect(status().isOk());
	}

	@Test
	void technicien_ne_peut_pas_consulter_le_journal_d_audit() throws Exception {
		mockMvc.perform(get("/api/v1/audit-log").with(user("tech").roles("TECHNICIEN")))
				.andExpect(status().isForbidden());
	}

	@Test
	void observateur_ne_peut_pas_consulter_le_journal_d_audit() throws Exception {
		mockMvc.perform(get("/api/v1/audit-log").with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isForbidden());
	}
}
