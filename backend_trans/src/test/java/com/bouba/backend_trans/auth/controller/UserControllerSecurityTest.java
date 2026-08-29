package com.bouba.backend_trans.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.Role;
import com.bouba.backend_trans.auth.service.UserService;
import com.bouba.backend_trans.config.MethodSecurityTestConfig;

/**
 * Vérifie la matrice RBAC (§4.4) de {@link UserController} : la gestion des
 * comptes utilisateurs (lecture comme écriture) est réservée au seul
 * administrateur.
 */
@WebMvcTest(UserController.class)
@Import(MethodSecurityTestConfig.class)
class UserControllerSecurityTest {

	private static final String CORPS_VALIDE = """
			{"username":"marie","email":"marie@exemple.sn","password":"motdepasse123","role":"OBSERVATEUR"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	void administrateur_peut_lister_les_utilisateurs() throws Exception {
		when(userService.findAll()).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/users").with(user("admin").roles("ADMINISTRATEUR")))
				.andExpect(status().isOk());
	}

	@Test
	void administrateur_peut_creer_un_utilisateur() throws Exception {
		when(userService.create(any())).thenReturn(utilisateur());

		mockMvc.perform(post("/api/v1/users")
						.with(user("admin").roles("ADMINISTRATEUR"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(CORPS_VALIDE))
				.andExpect(status().isCreated());
	}

	@Test
	void technicien_ne_peut_pas_lister_les_utilisateurs() throws Exception {
		mockMvc.perform(get("/api/v1/users").with(user("tech").roles("TECHNICIEN")))
				.andExpect(status().isForbidden());
	}

	@Test
	void observateur_ne_peut_pas_lister_les_utilisateurs() throws Exception {
		mockMvc.perform(get("/api/v1/users").with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isForbidden());
	}

	@Test
	void technicien_ne_peut_pas_creer_un_utilisateur() throws Exception {
		mockMvc.perform(post("/api/v1/users")
						.with(user("tech").roles("TECHNICIEN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(CORPS_VALIDE))
				.andExpect(status().isForbidden());
	}

	@Test
	void technicien_ne_peut_pas_desactiver_un_utilisateur() throws Exception {
		mockMvc.perform(delete("/api/v1/users/{id}", 1L).with(user("tech").roles("TECHNICIEN")))
				.andExpect(status().isForbidden());
	}

	private AppUser utilisateur() {
		AppUser utilisateur = new AppUser();
		utilisateur.setId(1L);
		utilisateur.setUsername("marie");
		utilisateur.setEmail("marie@exemple.sn");
		utilisateur.setRole(Role.OBSERVATEUR);
		return utilisateur;
	}
}
