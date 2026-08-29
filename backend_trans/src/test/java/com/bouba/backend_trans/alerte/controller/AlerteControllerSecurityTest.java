package com.bouba.backend_trans.alerte.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.StatutAlerte;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;
import com.bouba.backend_trans.alerte.service.AlerteService;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.Role;
import com.bouba.backend_trans.auth.repository.AppUserRepository;
import com.bouba.backend_trans.config.MethodSecurityTestConfig;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.TypeEquipement;

/**
 * Vérifie que la matrice RBAC (§4.4) est bien appliquée par les
 * {@code @PreAuthorize} de {@link AlerteController} : lecture ouverte aux
 * trois rôles, prise en compte/résolution réservées à administrateur et
 * technicien.
 */
@WebMvcTest(AlerteController.class)
@Import(MethodSecurityTestConfig.class)
class AlerteControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AlerteService alerteService;

	@MockitoBean
	private AppUserRepository appUserRepository;

	// --- lecture (GET), ouverte aux trois rôles ---

	@Test
	void observateur_peut_lister_les_alertes() throws Exception {
		when(alerteService.rechercher(any(), any(), any())).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/alerts").with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isOk());
	}

	@Test
	void technicien_peut_consulter_une_alerte() throws Exception {
		UUID id = UUID.randomUUID();
		when(alerteService.findById(id)).thenReturn(alerte(id));

		mockMvc.perform(get("/api/v1/alerts/{id}", id).with(user("tech").roles("TECHNICIEN")))
				.andExpect(status().isOk());
	}

	@Test
	void administrateur_peut_consulter_une_alerte() throws Exception {
		UUID id = UUID.randomUUID();
		when(alerteService.findById(id)).thenReturn(alerte(id));

		mockMvc.perform(get("/api/v1/alerts/{id}", id).with(user("admin").roles("ADMINISTRATEUR")))
				.andExpect(status().isOk());
	}

	// --- prise en compte / résolution, réservées administrateur+technicien ---

	@Test
	void technicien_peut_prendre_en_compte_une_alerte() throws Exception {
		UUID id = UUID.randomUUID();
		AppUser utilisateur = utilisateur("marie@exemple.sn", Role.TECHNICIEN);
		when(appUserRepository.findByEmail("marie@exemple.sn")).thenReturn(Optional.of(utilisateur));
		when(alerteService.acknowledge(id, utilisateur)).thenReturn(alerte(id));

		mockMvc.perform(put("/api/v1/alerts/{id}/acknowledge", id)
						.with(user("marie@exemple.sn").roles("TECHNICIEN")))
				.andExpect(status().isOk());
	}

	@Test
	void administrateur_peut_resoudre_une_alerte() throws Exception {
		UUID id = UUID.randomUUID();
		when(alerteService.resolve(id)).thenReturn(alerte(id));

		mockMvc.perform(put("/api/v1/alerts/{id}/resolve", id).with(user("admin").roles("ADMINISTRATEUR")))
				.andExpect(status().isOk());
	}

	@Test
	void observateur_ne_peut_pas_prendre_en_compte_une_alerte() throws Exception {
		mockMvc.perform(put("/api/v1/alerts/{id}/acknowledge", UUID.randomUUID())
						.with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isForbidden());
	}

	@Test
	void observateur_ne_peut_pas_resoudre_une_alerte() throws Exception {
		mockMvc.perform(put("/api/v1/alerts/{id}/resolve", UUID.randomUUID())
						.with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isForbidden());
	}

	// --- fixtures ---

	private Alerte alerte(UUID id) {
		Equipement equipement = new Equipement();
		equipement.setId(UUID.randomUUID());
		equipement.setNom("Routeur coeur");
		equipement.setAdresseIp("10.0.0.1");
		equipement.setType(TypeEquipement.ROUTEUR);
		equipement.setEtat(EtatEquipement.ACTIF);

		Alerte alerte = new Alerte();
		alerte.setId(id);
		alerte.setEquipement(equipement);
		alerte.setTypeAnomalie(TypeAnomalie.CPU);
		alerte.setSeverite(Severite.CRITIQUE);
		alerte.setStatut(StatutAlerte.DECLENCHEE);
		alerte.setDateDeclenchement(LocalDateTime.now());
		return alerte;
	}

	private AppUser utilisateur(String email, Role role) {
		AppUser utilisateur = new AppUser();
		utilisateur.setId(1L);
		utilisateur.setUsername("marie");
		utilisateur.setEmail(email);
		utilisateur.setRole(role);
		return utilisateur;
	}
}
