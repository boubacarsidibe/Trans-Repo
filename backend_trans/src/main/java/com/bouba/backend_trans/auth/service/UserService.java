package com.bouba.backend_trans.auth.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.alerte.repository.AlerteRepository;
import com.bouba.backend_trans.audit.Auditable;
import com.bouba.backend_trans.audit.repository.JournalAuditRepository;
import com.bouba.backend_trans.auth.dto.UserCreateRequest;
import com.bouba.backend_trans.auth.dto.UserUpdateRequest;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.UserType;
import com.bouba.backend_trans.auth.repository.AppUserRepository;
import com.bouba.backend_trans.maintenance.repository.FenetreMaintenanceRepository;

@Service
public class UserService {

	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final JournalAuditRepository journalAuditRepository;
	private final AlerteRepository alerteRepository;
	private final FenetreMaintenanceRepository fenetreMaintenanceRepository;

	public UserService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
			JournalAuditRepository journalAuditRepository, AlerteRepository alerteRepository,
			FenetreMaintenanceRepository fenetreMaintenanceRepository) {
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.journalAuditRepository = journalAuditRepository;
		this.alerteRepository = alerteRepository;
		this.fenetreMaintenanceRepository = fenetreMaintenanceRepository;
	}

	@Transactional(readOnly = true)
	public List<AppUser> findAll() {
		return appUserRepository.findAll();
	}

	@Transactional(readOnly = true)
	public AppUser findById(Long id) {
		return appUserRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
	}

	@Transactional
	@Auditable("CREATION_UTILISATEUR")
	public AppUser create(UserCreateRequest request) {
		String normalizedEmail = request.getEmail().trim().toLowerCase();
		if (appUserRepository.existsByEmail(normalizedEmail)) {
			throw new IllegalStateException("Un compte avec cet e-mail existe déjà.");
		}

		AppUser user = new AppUser();
		user.setUsername(request.getUsername().trim());
		user.setEmail(normalizedEmail);
		user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
		user.setRole(request.getRole());
		user.setUserType(request.getUserType() == null ? UserType.INDIVIDUAL : request.getUserType());
		return appUserRepository.save(user);
	}

	@Transactional
	@Auditable("MODIFICATION_UTILISATEUR")
	public AppUser update(Long id, UserUpdateRequest request) {
		AppUser user = findById(id);

		String normalizedEmail = request.getEmail().trim().toLowerCase();
		if (!user.getEmail().equals(normalizedEmail) && appUserRepository.existsByEmail(normalizedEmail)) {
			throw new IllegalStateException("Un compte avec cet e-mail existe déjà.");
		}

		user.setUsername(request.getUsername().trim());
		user.setEmail(normalizedEmail);
		user.setRole(request.getRole());
		user.setUserType(request.getUserType() == null ? user.getUserType() : request.getUserType());
		user.setActive(request.isActive());
		if (request.getPassword() != null && !request.getPassword().isBlank()) {
			user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
		}
		return appUserRepository.save(user);
	}

	@Transactional
	@Auditable("DESACTIVATION_UTILISATEUR")
	public void deactivate(Long id) {
		AppUser user = findById(id);
		user.setActive(false);
		appUserRepository.save(user);
	}

	/**
	 * Suppression réelle de la ligne (issue #179), à l'inverse de {@link #deactivate}
	 * qui ne fait que masquer le compte. N'est autorisée que si l'utilisateur ne
	 * conserve strictement aucune trace : {@code journal_audit.utilisateur_id} est
	 * une clé étrangère NOT NULL (pas un instantané), tout comme
	 * {@code Alerte.utilisateurPriseEnCharge} et {@code FenetreMaintenance.creePar} —
	 * les supprimer casserait ces références. On ne peut jamais se supprimer
	 * soi-même.
	 */
	@Transactional
	@Auditable("SUPPRESSION_UTILISATEUR")
	public void supprimerDefinitivement(Long id, AppUser utilisateurCourant) {
		AppUser utilisateur = findById(id);

		List<String> blocages = new ArrayList<>();
		if (journalAuditRepository.existsByUtilisateurId(id)) {
			blocages.add("des entrées du journal d'audit");
		}
		if (alerteRepository.existsByUtilisateurPriseEnChargeId(id)) {
			blocages.add("des alertes prises en charge");
		}
		if (fenetreMaintenanceRepository.existsByCreeParId(id)) {
			blocages.add("des fenêtres de maintenance créées");
		}

		if (!blocages.isEmpty()) {
			throw new IllegalStateException(
					"Impossible de supprimer définitivement " + utilisateur.getUsername() + " : il conserve "
							+ String.join(", ", blocages) + ". Désactivez-le à la place.");
		}

		if (utilisateurCourant != null && utilisateur.getId().equals(utilisateurCourant.getId())) {
			throw new IllegalStateException(
					"Impossible de supprimer définitivement son propre compte. Désactivez-le à la place.");
		}

		appUserRepository.delete(utilisateur);
	}
}
