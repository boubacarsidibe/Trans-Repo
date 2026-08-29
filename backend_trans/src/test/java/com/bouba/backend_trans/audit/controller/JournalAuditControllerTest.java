package com.bouba.backend_trans.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.bouba.backend_trans.audit.dto.JournalAuditResponse;
import com.bouba.backend_trans.audit.entity.JournalAudit;
import com.bouba.backend_trans.audit.service.JournalAuditService;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.Role;

/**
 * Le contrôleur se borne d'habitude à déléguer au service ; ici il porte
 * en plus le calage des bornes de pagination (§7.9), d'où ce test dédié.
 */
@ExtendWith(MockitoExtension.class)
class JournalAuditControllerTest {

	@Mock
	private JournalAuditService journalAuditService;

	private JournalAuditController journalAuditController;

	@BeforeEach
	void initController() {
		journalAuditController = new JournalAuditController(journalAuditService);
	}

	// --- calage des bornes de pagination ---

	@Test
	void applique_la_page_et_la_taille_par_defaut() {
		when(journalAuditService.findAll(PageRequest.of(0, 200))).thenReturn(List.of());

		journalAuditController.list(0, 200);

		verify(journalAuditService).findAll(PageRequest.of(0, 200));
	}

	@Test
	void ramene_une_page_negative_a_zero() {
		when(journalAuditService.findAll(PageRequest.of(0, 50))).thenReturn(List.of());

		journalAuditController.list(-5, 50);

		verify(journalAuditService).findAll(PageRequest.of(0, 50));
	}

	@Test
	void impose_une_taille_de_page_d_au_moins_une_ligne() {
		when(journalAuditService.findAll(PageRequest.of(0, 1))).thenReturn(List.of());

		journalAuditController.list(0, 0);

		verify(journalAuditService).findAll(PageRequest.of(0, 1));
	}

	@Test
	void plafonne_la_taille_de_page_a_mille_lignes() {
		when(journalAuditService.findAll(PageRequest.of(0, 1000))).thenReturn(List.of());

		journalAuditController.list(0, 5000);

		verify(journalAuditService).findAll(PageRequest.of(0, 1000));
	}

	// --- conversion en DTO ---

	@Test
	void convertit_chaque_entree_du_journal_en_reponse() {
		AppUser utilisateur = utilisateur("marie@exemple.sn");
		JournalAudit entree = entree(utilisateur, "SUPPRESSION_EQUIPEMENT", "10.0.0.5");
		when(journalAuditService.findAll(PageRequest.of(0, 200))).thenReturn(List.of(entree));

		List<JournalAuditResponse> reponses = journalAuditController.list(0, 200);

		assertThat(reponses).hasSize(1);
		JournalAuditResponse reponse = reponses.get(0);
		assertThat(reponse.getId()).isEqualTo(entree.getId());
		assertThat(reponse.getUtilisateurEmail()).isEqualTo("marie@exemple.sn");
		assertThat(reponse.getAction()).isEqualTo("SUPPRESSION_EQUIPEMENT");
		assertThat(reponse.getAdresseIpSource()).isEqualTo("10.0.0.5");
	}

	// --- fixtures ---

	private AppUser utilisateur(String email) {
		AppUser utilisateur = new AppUser();
		utilisateur.setId(1L);
		utilisateur.setUsername("marie");
		utilisateur.setEmail(email);
		utilisateur.setRole(Role.ADMINISTRATEUR);
		return utilisateur;
	}

	private JournalAudit entree(AppUser utilisateur, String action, String adresseIp) {
		JournalAudit entree = new JournalAudit();
		entree.setId(7L);
		entree.setUtilisateur(utilisateur);
		entree.setAction(action);
		entree.setAdresseIpSource(adresseIp);
		entree.setHorodatage(LocalDateTime.now());
		return entree;
	}
}
