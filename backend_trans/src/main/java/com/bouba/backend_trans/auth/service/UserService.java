package com.bouba.backend_trans.auth.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.auth.dto.UserCreateRequest;
import com.bouba.backend_trans.auth.dto.UserUpdateRequest;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.UserType;
import com.bouba.backend_trans.auth.repository.AppUserRepository;

@Service
public class UserService {

	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
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
	public void deactivate(Long id) {
		AppUser user = findById(id);
		user.setActive(false);
		appUserRepository.save(user);
	}
}
