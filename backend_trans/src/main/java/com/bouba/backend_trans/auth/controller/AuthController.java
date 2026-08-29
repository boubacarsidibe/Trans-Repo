package com.bouba.backend_trans.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.auth.dto.AuthResponse;
import com.bouba.backend_trans.auth.dto.ForgotPasswordRequest;
import com.bouba.backend_trans.auth.dto.LoginRequest;
import com.bouba.backend_trans.auth.dto.RefreshRequest;
import com.bouba.backend_trans.auth.dto.RegisterRequest;
import com.bouba.backend_trans.auth.dto.ResetPasswordRequest;
import com.bouba.backend_trans.auth.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	// Restreint aux administrateurs (issue #8) : la création de compte publique
	// produisait des comptes hors matrice RBAC (§4.4). L'auto-inscription reste
	// désactivée ; POST /api/v1/users couvre déjà la création par un admin.
	@PostMapping("/register")
	@PreAuthorize("hasRole('ADMINISTRATEUR')")
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		AuthResponse response = authService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		AuthResponse response = authService.login(request);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		AuthResponse response = authService.refresh(request);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		authService.forgotPassword(request);
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/reset-password")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		authService.resetPassword(request);
		return ResponseEntity.ok().build();
	}
}
