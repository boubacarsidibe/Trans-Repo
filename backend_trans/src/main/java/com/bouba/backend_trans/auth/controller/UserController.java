package com.bouba.backend_trans.auth.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.auth.dto.UserCreateRequest;
import com.bouba.backend_trans.auth.dto.UserResponse;
import com.bouba.backend_trans.auth.dto.UserUpdateRequest;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.repository.AppUserRepository;
import com.bouba.backend_trans.auth.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class UserController {

	private final UserService userService;
	private final AppUserRepository appUserRepository;

	public UserController(UserService userService, AppUserRepository appUserRepository) {
		this.userService = userService;
		this.appUserRepository = appUserRepository;
	}

	@GetMapping
	public List<UserResponse> list() {
		return userService.findAll().stream()
				.map(UserResponse::fromEntity)
				.collect(Collectors.toList());
	}

	@PostMapping
	public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
		AppUser created = userService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.fromEntity(created));
	}

	@PutMapping("/{id}")
	public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
		return UserResponse.fromEntity(userService.update(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deactivate(@PathVariable Long id) {
		userService.deactivate(id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Suppression définitive (issue #179). Déjà réservée à l'administrateur par
	 * la restriction de classe — pas besoin de la répéter ici, contrairement à
	 * l'équivalent équipements dont le contrôleur autorise plus largement. Le
	 * service refuse (409) si l'utilisateur conserve la moindre trace, ou s'il
	 * s'agit du compte actuellement authentifié.
	 */
	@DeleteMapping("/{id}/definitif")
	public ResponseEntity<Void> supprimerDefinitivement(@PathVariable Long id, Authentication authentication) {
		userService.supprimerDefinitivement(id, currentUser(authentication));
		return ResponseEntity.noContent().build();
	}

	private AppUser currentUser(Authentication authentication) {
		return appUserRepository.findByEmail(authentication.getName())
				.orElseThrow(() -> new IllegalStateException("Utilisateur authentifié introuvable."));
	}
}
