package com.bouba.backend_trans.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.dto.auth.AuthResponse;
import com.bouba.backend_trans.dto.auth.LoginRequest;
import com.bouba.backend_trans.dto.auth.RegisterRequest;
import com.bouba.backend_trans.entity.AppUser;
import com.bouba.backend_trans.mapper.AuthMapper;
import com.bouba.backend_trans.repository.AppUserRepository;
import com.bouba.backend_trans.security.JwtService;
import com.bouba.backend_trans.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {

	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthMapper authMapper;
	private final JwtService jwtService;

	public AuthServiceImpl(
			AppUserRepository appUserRepository,
			PasswordEncoder passwordEncoder,
			AuthMapper authMapper,
			JwtService jwtService
	) {
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.authMapper = authMapper;
		this.jwtService = jwtService;
	}

	@Override
	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String normalizedEmail = request.getEmail().trim().toLowerCase();
		if (appUserRepository.existsByEmail(normalizedEmail)) {
			throw new IllegalStateException("An account with this email already exists.");
		}

		String encodedPassword = passwordEncoder.encode(request.getPassword());
		AppUser user = authMapper.toEntity(request, encodedPassword);
		AppUser savedUser = appUserRepository.save(user);
		String token = jwtService.generateToken(savedUser);

		AuthResponse response = authMapper.toResponse(savedUser, "Registration successful.");
		response.setToken(token);
		return response;
	}

	@Override
	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		String normalizedEmail = request.getEmail().trim().toLowerCase();
		AppUser user = appUserRepository.findByEmail(normalizedEmail)
				.orElseThrow(() -> new IllegalArgumentException("Invalid credentials."));

		if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
			throw new IllegalArgumentException("Invalid credentials.");
		}

		String token = jwtService.generateToken(user);
		AuthResponse response = authMapper.toResponse(user, "Login successful.");
		response.setToken(token);
		return response;
	}
}
