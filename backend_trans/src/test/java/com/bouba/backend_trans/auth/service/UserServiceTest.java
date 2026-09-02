package com.bouba.backend_trans.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.bouba.backend_trans.alerte.repository.AlerteRepository;
import com.bouba.backend_trans.audit.repository.JournalAuditRepository;
import com.bouba.backend_trans.auth.dto.UserCreateRequest;
import com.bouba.backend_trans.auth.dto.UserUpdateRequest;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.Role;
import com.bouba.backend_trans.auth.entity.UserType;
import com.bouba.backend_trans.auth.repository.AppUserRepository;
import com.bouba.backend_trans.maintenance.repository.FenetreMaintenanceRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private AppUserRepository appUserRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JournalAuditRepository journalAuditRepository;

	@Mock
	private AlerteRepository alerteRepository;

	@Mock
	private FenetreMaintenanceRepository fenetreMaintenanceRepository;

	private UserService userService;

	@BeforeEach
	void initService() {
		userService = new UserService(appUserRepository, passwordEncoder, journalAuditRepository, alerteRepository,
				fenetreMaintenanceRepository);
	}

	// --- findAll / findById ---

	@Test
	void retourne_tous_les_utilisateurs() {
		List<AppUser> utilisateurs = List.of(utilisateur(1L, "alice"));
		when(appUserRepository.findAll()).thenReturn(utilisateurs);

		assertThat(userService.findAll()).isEqualTo(utilisateurs);
	}

	@Test
	void retourne_l_utilisateur_correspondant_a_l_identifiant() {
		AppUser utilisateur = utilisateur(1L, "alice");
		when(appUserRepository.findById(1L)).thenReturn(Optional.of(utilisateur));

		assertThat(userService.findById(1L)).isEqualTo(utilisateur);
	}

	@Test
	void leve_une_exception_quand_l_utilisateur_est_introuvable() {
		when(appUserRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.findById(1L))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Utilisateur introuvable.");
	}

	// --- create ---

	@Test
	void cree_un_utilisateur_avec_un_e_mail_normalise() {
		UserCreateRequest request = new UserCreateRequest();
		request.setUsername("Alice");
		request.setEmail("Alice@Exemple.com");
		request.setPassword("motdepasse123");
		request.setRole(Role.OBSERVATEUR);
		when(appUserRepository.existsByEmail("alice@exemple.com")).thenReturn(false);
		when(passwordEncoder.encode("motdepasse123")).thenReturn("hash");
		when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		AppUser cree = userService.create(request);

		assertThat(cree.getEmail()).isEqualTo("alice@exemple.com");
		assertThat(cree.getPasswordHash()).isEqualTo("hash");
		assertThat(cree.getUserType()).isEqualTo(UserType.INDIVIDUAL);
	}

	@Test
	void rejette_la_creation_d_un_compte_dont_l_e_mail_existe_deja() {
		UserCreateRequest request = new UserCreateRequest();
		request.setUsername("Alice");
		request.setEmail("alice@exemple.com");
		request.setPassword("motdepasse123");
		request.setRole(Role.OBSERVATEUR);
		when(appUserRepository.existsByEmail("alice@exemple.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.create(request))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Un compte avec cet e-mail existe déjà.");

		verify(appUserRepository, never()).save(any());
	}

	// --- update ---

	@Test
	void modifie_les_champs_d_un_utilisateur_existant() {
		AppUser existant = utilisateur(1L, "alice");
		when(appUserRepository.findById(1L)).thenReturn(Optional.of(existant));
		when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		UserUpdateRequest request = new UserUpdateRequest();
		request.setUsername("Alice Dupont");
		request.setEmail("alice@exemple.com");
		request.setRole(Role.TECHNICIEN);
		request.setActive(true);

		AppUser modifie = userService.update(1L, request);

		assertThat(modifie.getUsername()).isEqualTo("Alice Dupont");
		assertThat(modifie.getRole()).isEqualTo(Role.TECHNICIEN);
	}

	// --- deactivate ---

	@Test
	void desactive_un_utilisateur() {
		AppUser existant = utilisateur(1L, "alice");
		when(appUserRepository.findById(1L)).thenReturn(Optional.of(existant));
		when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		userService.deactivate(1L);

		assertThat(existant.isActive()).isFalse();
		verify(appUserRepository).save(existant);
	}

	// --- supprimerDefinitivement ---

	@Test
	void supprime_definitivement_un_utilisateur_sans_aucune_trace() {
		AppUser existant = utilisateur(1L, "alice");
		AppUser courant = utilisateur(2L, "bob");
		when(appUserRepository.findById(1L)).thenReturn(Optional.of(existant));
		when(journalAuditRepository.existsByUtilisateurId(1L)).thenReturn(false);
		when(alerteRepository.existsByUtilisateurPriseEnChargeId(1L)).thenReturn(false);
		when(fenetreMaintenanceRepository.existsByCreeParId(1L)).thenReturn(false);

		userService.supprimerDefinitivement(1L, courant);

		verify(appUserRepository).delete(existant);
	}

	@Test
	void refuse_la_suppression_definitive_d_un_utilisateur_avec_des_entrees_de_journal() {
		AppUser existant = utilisateur(1L, "alice");
		AppUser courant = utilisateur(2L, "bob");
		when(appUserRepository.findById(1L)).thenReturn(Optional.of(existant));
		when(journalAuditRepository.existsByUtilisateurId(1L)).thenReturn(true);

		assertThatThrownBy(() -> userService.supprimerDefinitivement(1L, courant))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("des entrées du journal d'audit")
				.hasMessageContaining("Désactivez-le à la place");

		verify(appUserRepository, never()).delete(any());
	}

	@Test
	void refuse_la_suppression_definitive_d_un_utilisateur_avec_des_alertes_prises_en_charge() {
		AppUser existant = utilisateur(1L, "alice");
		AppUser courant = utilisateur(2L, "bob");
		when(appUserRepository.findById(1L)).thenReturn(Optional.of(existant));
		when(journalAuditRepository.existsByUtilisateurId(1L)).thenReturn(false);
		when(alerteRepository.existsByUtilisateurPriseEnChargeId(1L)).thenReturn(true);

		assertThatThrownBy(() -> userService.supprimerDefinitivement(1L, courant))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("des alertes prises en charge");

		verify(appUserRepository, never()).delete(any());
	}

	@Test
	void refuse_la_suppression_definitive_d_un_utilisateur_avec_des_fenetres_de_maintenance_creees() {
		AppUser existant = utilisateur(1L, "alice");
		AppUser courant = utilisateur(2L, "bob");
		when(appUserRepository.findById(1L)).thenReturn(Optional.of(existant));
		when(journalAuditRepository.existsByUtilisateurId(1L)).thenReturn(false);
		when(alerteRepository.existsByUtilisateurPriseEnChargeId(1L)).thenReturn(false);
		when(fenetreMaintenanceRepository.existsByCreeParId(1L)).thenReturn(true);

		assertThatThrownBy(() -> userService.supprimerDefinitivement(1L, courant))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("des fenêtres de maintenance créées");

		verify(appUserRepository, never()).delete(any());
	}

	@Test
	void refuse_la_suppression_definitive_de_son_propre_compte() {
		AppUser existant = utilisateur(1L, "alice");
		when(appUserRepository.findById(1L)).thenReturn(Optional.of(existant));
		when(journalAuditRepository.existsByUtilisateurId(1L)).thenReturn(false);
		when(alerteRepository.existsByUtilisateurPriseEnChargeId(1L)).thenReturn(false);
		when(fenetreMaintenanceRepository.existsByCreeParId(1L)).thenReturn(false);

		assertThatThrownBy(() -> userService.supprimerDefinitivement(1L, existant))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Impossible de supprimer définitivement son propre compte. Désactivez-le à la place.");

		verify(appUserRepository, never()).delete(any());
	}

	@Test
	void leve_une_exception_lors_de_la_suppression_definitive_d_un_utilisateur_introuvable() {
		when(appUserRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.supprimerDefinitivement(1L, utilisateur(2L, "bob")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Utilisateur introuvable.");

		verify(appUserRepository, never()).delete(any());
	}

	// --- fixtures ---

	private AppUser utilisateur(Long id, String username) {
		AppUser utilisateur = new AppUser();
		utilisateur.setId(id);
		utilisateur.setUsername(username);
		utilisateur.setEmail(username + "@exemple.com");
		utilisateur.setPasswordHash("hash");
		utilisateur.setRole(Role.OBSERVATEUR);
		utilisateur.setUserType(UserType.INDIVIDUAL);
		utilisateur.setActive(true);
		return utilisateur;
	}
}
