package com.bouba.backend_trans.notification;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.repository.AppUserRepository;

/**
 * « Chaque utilisateur peut configurer les canaux de notification qu'il
 * souhaite recevoir » (règle F7) — d'où un réglage personnel, accessible à tout
 * compte authentifié pour lui-même et pour lui seul.
 */
@RestController
@RequestMapping("/api/v1/users/me/notifications")
public class PreferenceNotificationController {

	private final AppUserRepository appUserRepository;

	public PreferenceNotificationController(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public Map<String, Boolean> lire(Authentication authentication) {
		return Map.of("emailActif", utilisateur(authentication).isNotificationsEmail());
	}

	@PutMapping
	@Transactional
	public Map<String, Boolean> modifier(
			Authentication authentication,
			@RequestBody Map<String, Boolean> corps
	) {
		AppUser utilisateur = utilisateur(authentication);
		utilisateur.setNotificationsEmail(Boolean.TRUE.equals(corps.get("emailActif")));
		appUserRepository.save(utilisateur);

		return Map.of("emailActif", utilisateur.isNotificationsEmail());
	}

	private AppUser utilisateur(Authentication authentication) {
		return appUserRepository.findByEmail(authentication.getName())
				.orElseThrow(() -> new IllegalArgumentException("Compte introuvable."));
	}
}
