package com.bouba.backend_trans.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.bouba.backend_trans.audit.entity.JournalAudit;
import com.bouba.backend_trans.audit.repository.JournalAuditRepository;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.Role;

@ExtendWith(MockitoExtension.class)
class JournalAuditServiceTest {

	@Mock
	private JournalAuditRepository journalAuditRepository;

	private JournalAuditService journalAuditService;

	@BeforeEach
	void initService() {
		journalAuditService = new JournalAuditService(journalAuditRepository);
	}

	// --- enregistrement d'une entrée ---

	@Test
	void enregistre_une_entree_avec_l_utilisateur_l_action_et_l_adresse_ip() {
		AppUser utilisateur = utilisateur("marie@exemple.sn");
		when(journalAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		journalAuditService.enregistrer(utilisateur, "SUPPRESSION_EQUIPEMENT", "10.0.0.5");

		ArgumentCaptor<JournalAudit> captor = ArgumentCaptor.forClass(JournalAudit.class);
		verify(journalAuditRepository).save(captor.capture());
		JournalAudit entree = captor.getValue();
		assertThat(entree.getUtilisateur()).isEqualTo(utilisateur);
		assertThat(entree.getAction()).isEqualTo("SUPPRESSION_EQUIPEMENT");
		assertThat(entree.getAdresseIpSource()).isEqualTo("10.0.0.5");
	}

	@Test
	void enregistre_une_entree_meme_sans_adresse_ip_source() {
		AppUser utilisateur = utilisateur("marie@exemple.sn");
		when(journalAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		journalAuditService.enregistrer(utilisateur, "CONNEXION", null);

		ArgumentCaptor<JournalAudit> captor = ArgumentCaptor.forClass(JournalAudit.class);
		verify(journalAuditRepository).save(captor.capture());
		assertThat(captor.getValue().getAdresseIpSource()).isNull();
	}

	// --- consultation : la plus récente d'abord (§7.9) ---

	@Test
	void retourne_le_journal_complet_du_plus_recent_au_plus_ancien() {
		List<JournalAudit> journal = List.of(entree(utilisateur("admin@exemple.sn"), "CONNEXION"));
		when(journalAuditRepository.findAllByOrderByHorodatageDesc()).thenReturn(journal);

		assertThat(journalAuditService.findAll()).isEqualTo(journal);
	}

	@Test
	void retourne_le_journal_pagine_du_plus_recent_au_plus_ancien() {
		Pageable pageable = PageRequest.of(2, 50);
		List<JournalAudit> page = List.of(entree(utilisateur("admin@exemple.sn"), "MODIFICATION_SEUIL"));
		when(journalAuditRepository.findAllByOrderByHorodatageDesc(pageable)).thenReturn(page);

		List<JournalAudit> obtenu = journalAuditService.findAll(pageable);

		assertThat(obtenu).isEqualTo(page);
		verify(journalAuditRepository).findAllByOrderByHorodatageDesc(pageable);
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

	private JournalAudit entree(AppUser utilisateur, String action) {
		JournalAudit entree = new JournalAudit();
		entree.setId(1L);
		entree.setUtilisateur(utilisateur);
		entree.setAction(action);
		return entree;
	}
}
