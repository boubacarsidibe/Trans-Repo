package com.bouba.backend_trans.equipement.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.equipement.entity.EtatEquipement;
import com.bouba.backend_trans.equipement.entity.TypeEquipement;
import com.bouba.backend_trans.equipement.service.EquipementService;

/**
 * Vérifie la matrice RBAC (§4.4) de {@link EquipementController} : lecture
 * ouverte aux trois rôles, création/modification/archivage réservées à
 * administrateur et technicien.
 */
@WebMvcTest(EquipementController.class)
@Import(MethodSecurityTestConfig.class)
class EquipementControllerSecurityTest {

	private static final String CORPS_VALIDE = """
			{"nom":"Routeur coeur","adresseIp":"10.0.0.1","type":"ROUTEUR"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EquipementService equipementService;

	@Test
	void observateur_peut_lister_les_equipements() throws Exception {
		when(equipementService.findAll()).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/equipments").with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isOk());
	}

	@Test
	void observateur_peut_consulter_un_equipement() throws Exception {
		UUID id = UUID.randomUUID();
		when(equipementService.findById(id)).thenReturn(equipement(id));

		mockMvc.perform(get("/api/v1/equipments/{id}", id).with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isOk());
	}

	@Test
	void technicien_peut_creer_un_equipement() throws Exception {
		when(equipementService.create(org.mockito.ArgumentMatchers.any())).thenReturn(equipement(UUID.randomUUID()));

		mockMvc.perform(post("/api/v1/equipments")
						.with(user("tech").roles("TECHNICIEN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(CORPS_VALIDE))
				.andExpect(status().isCreated());
	}

	@Test
	void administrateur_peut_archiver_un_equipement() throws Exception {
		mockMvc.perform(delete("/api/v1/equipments/{id}", UUID.randomUUID())
						.with(user("admin").roles("ADMINISTRATEUR")))
				.andExpect(status().isNoContent());
	}

	@Test
	void observateur_ne_peut_pas_creer_un_equipement() throws Exception {
		mockMvc.perform(post("/api/v1/equipments")
						.with(user("obs").roles("OBSERVATEUR"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(CORPS_VALIDE))
				.andExpect(status().isForbidden());
	}

	@Test
	void observateur_ne_peut_pas_archiver_un_equipement() throws Exception {
		mockMvc.perform(delete("/api/v1/equipments/{id}", UUID.randomUUID())
						.with(user("obs").roles("OBSERVATEUR")))
				.andExpect(status().isForbidden());
	}

	private Equipement equipement(UUID id) {
		Equipement equipement = new Equipement();
		equipement.setId(id);
		equipement.setNom("Routeur coeur");
		equipement.setAdresseIp("10.0.0.1");
		equipement.setType(TypeEquipement.ROUTEUR);
		equipement.setEtat(EtatEquipement.ACTIF);
		return equipement;
	}
}
