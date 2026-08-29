package com.bouba.backend_trans.audit;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.bouba.backend_trans.audit.service.JournalAuditService;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.repository.AppUserRepository;

/**
 * Met en œuvre {@link Auditable} : une seule fois, plutôt qu'un appel à
 * {@link JournalAuditService#enregistrer} recopié dans chaque service qui
 * mute quelque chose (issue #44).
 */
@Aspect
@Component
public class AuditAspect {

	private final JournalAuditService journalAuditService;
	private final AppUserRepository appUserRepository;

	public AuditAspect(JournalAuditService journalAuditService, AppUserRepository appUserRepository) {
		this.journalAuditService = journalAuditService;
		this.appUserRepository = appUserRepository;
	}

	@AfterReturning("@annotation(auditable)")
	public void auditerApresSucces(Auditable auditable) {
		AppUser utilisateur = utilisateurCourant();
		if (utilisateur == null) {
			// Pas de contexte de securite authentifie (appel systeme/planifie,
			// amorcage) : rien a auditer, l'entree ne serait de toute facon pas
			// acceptee par journal_audit.utilisateur (NOT NULL).
			return;
		}
		journalAuditService.enregistrer(utilisateur, auditable.value(), adresseIpSource());
	}

	private AppUser utilisateurCourant() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return null;
		}
		return appUserRepository.findByEmail(authentication.getName()).orElse(null);
	}

	private String adresseIpSource() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return null;
		}
		return attributes.getRequest().getRemoteAddr();
	}
}
