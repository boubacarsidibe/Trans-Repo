package com.bouba.backend_trans.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.bouba.backend_trans.alerte.dto.AlerteResponse;
import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.StatutAlerte;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.Role;
import com.bouba.backend_trans.auth.repository.AppUserRepository;
import com.bouba.backend_trans.equipement.entity.Equipement;
import com.bouba.backend_trans.websocket.EvenementSupervision;
import com.bouba.backend_trans.websocket.TypeEvenement;

/**
 * Le serveur SMTP est mocké (comme les autres tests de service du projet)
 * plutôt qu'un vrai faux serveur type GreenMail : le comportement testé ici
 * (qui est notifié, quand, avec quel contenu) ne dépend pas d'un protocole
 * SMTP réellement parlé, seulement des appels faits à {@link JavaMailSender}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationAlerteServiceTest {

	@Mock
	private AppUserRepository appUserRepository;

	@Mock
	private ObjectProvider<JavaMailSender> mailSenderProvider;

	@Mock
	private JavaMailSender mailSender;

	private AppUser technicien;

	@BeforeEach
	void setUp() {
		technicien = new AppUser();
		technicien.setEmail("technicien@ept.sn");
		technicien.setRole(Role.TECHNICIEN);
		technicien.setNotificationsEmail(true);
	}

	private NotificationAlerteService service(boolean actif) {
		return new NotificationAlerteService(appUserRepository, mailSenderProvider, actif, "supervision@ept.sn");
	}

	private AlerteResponse alerte(Severite severite) {
		Equipement equipement = new Equipement();
		equipement.setId(UUID.randomUUID());
		equipement.setNom("Routeur-Bloc-A");

		Alerte alerte = new Alerte();
		alerte.setId(UUID.randomUUID());
		alerte.setEquipement(equipement);
		alerte.setTypeAnomalie(TypeAnomalie.INDISPONIBILITE);
		alerte.setSeverite(severite);
		alerte.setStatut(StatutAlerte.DECLENCHEE);
		alerte.setDateDeclenchement(LocalDateTime.now());
		return AlerteResponse.fromEntity(alerte);
	}

	@Test
	void notifier_ne_fait_rien_si_les_notifications_sont_desactivees() {
		NotificationAlerteService service = service(false);

		service.notifier(alerte(Severite.CRITIQUE), false);

		verify(mailSenderProvider, never()).getIfAvailable();
	}

	@Test
	void notifier_ne_leve_pas_d_exception_si_aucun_serveur_smtp_n_est_configure() {
		when(mailSenderProvider.getIfAvailable()).thenReturn(null);
		NotificationAlerteService service = service(true);

		service.notifier(alerte(Severite.CRITIQUE), false);

		verify(appUserRepository, never()).findByActiveTrueAndRoleIn(any());
	}

	@Test
	void notifier_envoie_une_critique_meme_si_l_utilisateur_a_desactive_les_notifications_email() {
		technicien.setNotificationsEmail(false);
		when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
		when(appUserRepository.findByActiveTrueAndRoleIn(List.of(Role.ADMINISTRATEUR, Role.TECHNICIEN)))
				.thenReturn(List.of(technicien));
		NotificationAlerteService service = service(true);

		service.notifier(alerte(Severite.CRITIQUE), false);

		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(captor.capture());
		assertThat(captor.getValue().getTo()).containsExactly("technicien@ept.sn");
	}

	@Test
	void notifier_n_envoie_rien_a_un_utilisateur_ayant_desactive_les_notifications_pour_une_alerte_non_critique() {
		technicien.setNotificationsEmail(false);
		when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
		when(appUserRepository.findByActiveTrueAndRoleIn(List.of(Role.ADMINISTRATEUR, Role.TECHNICIEN)))
				.thenReturn(List.of(technicien));
		NotificationAlerteService service = service(true);

		service.notifier(alerte(Severite.AVERTISSEMENT), false);

		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	void notifier_prefixe_l_objet_par_rappel_quand_c_est_un_rappel() {
		when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
		when(appUserRepository.findByActiveTrueAndRoleIn(List.of(Role.ADMINISTRATEUR, Role.TECHNICIEN)))
				.thenReturn(List.of(technicien));
		NotificationAlerteService service = service(true);

		service.notifier(alerte(Severite.CRITIQUE), true);

		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(captor.capture());
		assertThat(captor.getValue().getSubject()).startsWith("RAPPEL ");
	}

	@Test
	void surEvenement_notifie_a_la_creation_et_a_la_mise_a_jour_d_une_alerte_uniquement() {
		when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
		when(appUserRepository.findByActiveTrueAndRoleIn(List.of(Role.ADMINISTRATEUR, Role.TECHNICIEN)))
				.thenReturn(List.of(technicien));
		NotificationAlerteService service = service(true);
		AlerteResponse alerte = alerte(Severite.CRITIQUE);

		service.surEvenement(EvenementSupervision.de(TypeEvenement.ALERT_CREATED, alerte));
		service.surEvenement(EvenementSupervision.de(TypeEvenement.METRIC_UPDATE, alerte));

		verify(mailSender, org.mockito.Mockito.times(1)).send(any(SimpleMailMessage.class));
	}
}
