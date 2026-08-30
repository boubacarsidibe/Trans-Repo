package com.bouba.backend_trans.notification;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bouba.backend_trans.alerte.dto.AlerteResponse;
import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.Role;
import com.bouba.backend_trans.auth.repository.AppUserRepository;
import com.bouba.backend_trans.websocket.EvenementSupervision;
import com.bouba.backend_trans.websocket.TypeEvenement;

/**
 * Notifications e-mail des alertes (F7).
 *
 * <p>Une alerte n'est notifiée qu'à sa création ou lorsque sa sévérité s'élève :
 * tant qu'elle reste ouverte, aucune relance n'est émise (anti-répétition
 * §11.4). Les rappels des critiques non prises en charge sont l'affaire de
 * {@link RappelAlertesCritiques}.
 */
@Component
public class NotificationAlerteService {

	private static final Logger log = LoggerFactory.getLogger(NotificationAlerteService.class);

	/** Seuls les rôles qui interviennent sur le parc sont notifiés (§4.4). */
	private static final List<Role> ROLES_NOTIFIES = List.of(Role.ADMINISTRATEUR, Role.TECHNICIEN);

	private final AppUserRepository appUserRepository;
	private final ObjectProvider<JavaMailSender> mailSender;
	private final boolean actif;
	private final String expediteur;

	public NotificationAlerteService(
			AppUserRepository appUserRepository,
			ObjectProvider<JavaMailSender> mailSender,
			@Value("${app.notifications.email.actif}") boolean actif,
			@Value("${app.notifications.email.expediteur}") String expediteur
	) {
		this.appUserRepository = appUserRepository;
		this.mailSender = mailSender;
		this.actif = actif;
		this.expediteur = expediteur;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void surEvenement(EvenementSupervision evenement) {
		boolean aNotifier = evenement.type() == TypeEvenement.ALERT_CREATED
				|| evenement.type() == TypeEvenement.ALERT_UPDATED;

		if (aNotifier && evenement.payload() instanceof AlerteResponse alerte) {
			notifier(alerte, false);
		}
	}

	public void notifier(AlerteResponse alerte, boolean rappel) {
		if (!actif) {
			return;
		}

		JavaMailSender expedition = mailSender.getIfAvailable();
		if (expedition == null) {
			log.warn("Notifications activées mais aucun serveur SMTP configuré : alerte {} non notifiée.", alerte.getId());
			return;
		}

		List<AppUser> destinataires = appUserRepository.findByActiveTrueAndRoleIn(ROLES_NOTIFIES).stream()
				// Une critique part toujours, même si l'utilisateur a coupé le
				// canal e-mail : c'est la règle F7.
				.filter(u -> u.isNotificationsEmail() || alerte.getSeverite() == Severite.CRITIQUE)
				.toList();

		if (destinataires.isEmpty()) {
			return;
		}

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(expediteur);
		message.setTo(destinataires.stream().map(AppUser::getEmail).toArray(String[]::new));
		message.setSubject(objet(alerte, rappel));
		message.setText(corps(alerte, rappel));

		try {
			expedition.send(message);
		} catch (Exception ex) {
			// Une messagerie injoignable ne doit jamais empêcher la supervision :
			// l'alerte reste visible sur le panneau et dans l'historique.
			log.error("Envoi de la notification pour l'alerte {} impossible : {}", alerte.getId(), ex.getMessage());
		}
	}

	private String objet(AlerteResponse alerte, boolean rappel) {
		return "%s[Supervision EPT] %s — %s".formatted(
				rappel ? "RAPPEL " : "",
				alerte.getSeverite(),
				alerte.getEquipementNom());
	}

	private String corps(AlerteResponse alerte, boolean rappel) {
		StringBuilder texte = new StringBuilder();
		if (rappel) {
			texte.append("Cette alerte critique n'a toujours pas été prise en charge.\n\n");
		}
		texte.append("Équipement : ").append(alerte.getEquipementNom()).append('\n');
		texte.append("Anomalie   : ").append(alerte.getTypeAnomalie()).append('\n');
		texte.append("Sévérité   : ").append(alerte.getSeverite()).append('\n');
		texte.append("Statut     : ").append(alerte.getStatut()).append('\n');
		texte.append("Déclenchée : ").append(alerte.getDateDeclenchement()).append('\n');
		return texte.toString();
	}
}
