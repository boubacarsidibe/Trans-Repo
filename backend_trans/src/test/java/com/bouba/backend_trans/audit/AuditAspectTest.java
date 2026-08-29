package com.bouba.backend_trans.audit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.bouba.backend_trans.audit.service.JournalAuditService;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.Role;
import com.bouba.backend_trans.auth.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

	@Mock
	private JournalAuditService journalAuditService;

	@Mock
	private AppUserRepository appUserRepository;

	@AfterEach
	void nettoyerContexte() {
		SecurityContextHolder.clearContext();
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void enregistre_l_action_l_utilisateur_et_l_adresse_ip_quand_authentifie() {
		AppUser utilisateur = utilisateur("marie@exemple.sn");
		authentifier("marie@exemple.sn");
		when(appUserRepository.findByEmail("marie@exemple.sn")).thenReturn(Optional.of(utilisateur));

		MockHttpServletRequest requete = new MockHttpServletRequest();
		requete.setRemoteAddr("10.0.0.5");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requete));

		Auditable auditable = auditable("MODIFICATION_EQUIPEMENT");
		new AuditAspect(journalAuditService, appUserRepository).auditerApresSucces(auditable);

		verify(journalAuditService).enregistrer(eq(utilisateur), eq("MODIFICATION_EQUIPEMENT"), eq("10.0.0.5"));
	}

	@Test
	void n_enregistre_rien_sans_utilisateur_authentifie() {
		SecurityContextHolder.getContext().setAuthentication(
				new AnonymousAuthenticationToken("cle", "anonymousUser", java.util.List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

		new AuditAspect(journalAuditService, appUserRepository).auditerApresSucces(auditable("CREATION_SEUIL"));

		verifyNoInteractions(journalAuditService);
	}

	@Test
	void n_enregistre_rien_sans_contexte_de_securite() {
		new AuditAspect(journalAuditService, appUserRepository).auditerApresSucces(auditable("SUPPRESSION_SEUIL"));

		verifyNoInteractions(journalAuditService);
	}

	@Test
	void enregistre_avec_une_adresse_ip_nulle_hors_requete_http() {
		AppUser utilisateur = utilisateur("admin@exemple.sn");
		authentifier("admin@exemple.sn");
		when(appUserRepository.findByEmail("admin@exemple.sn")).thenReturn(Optional.of(utilisateur));
		// Pas de RequestContextHolder configuré ici : simule un appel hors requête HTTP.

		new AuditAspect(journalAuditService, appUserRepository).auditerApresSucces(auditable("DESACTIVATION_UTILISATEUR"));

		verify(journalAuditService).enregistrer(eq(utilisateur), eq("DESACTIVATION_UTILISATEUR"), isNull());
	}

	private void authentifier(String email) {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(email, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATEUR"))));
	}

	private AppUser utilisateur(String email) {
		AppUser utilisateur = new AppUser();
		utilisateur.setId(1L);
		utilisateur.setUsername("marie");
		utilisateur.setEmail(email);
		utilisateur.setRole(Role.ADMINISTRATEUR);
		return utilisateur;
	}

	private Auditable auditable(String action) {
		return new Auditable() {
			@Override
			public Class<? extends java.lang.annotation.Annotation> annotationType() {
				return Auditable.class;
			}

			@Override
			public String value() {
				return action;
			}
		};
	}
}
