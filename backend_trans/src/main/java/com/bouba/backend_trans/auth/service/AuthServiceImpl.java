package com.bouba.backend_trans.auth.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.auth.dto.AuthResponse;
import com.bouba.backend_trans.auth.dto.ForgotPasswordRequest;
import com.bouba.backend_trans.auth.dto.LoginRequest;
import com.bouba.backend_trans.auth.dto.RefreshRequest;
import com.bouba.backend_trans.auth.dto.ResetPasswordRequest;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.PasswordResetToken;
import com.bouba.backend_trans.auth.entity.RefreshToken;
import com.bouba.backend_trans.auth.mapper.AuthMapper;
import com.bouba.backend_trans.auth.repository.AppUserRepository;
import com.bouba.backend_trans.auth.repository.PasswordResetTokenRepository;
import com.bouba.backend_trans.auth.repository.RefreshTokenRepository;
import com.bouba.backend_trans.auth.security.JwtService;
import com.bouba.backend_trans.exception.AccountLockedException;

@Service
public class AuthServiceImpl implements AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthMapper authMapper;
	private final JwtService jwtService;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;

	private final long refreshExpirationMs;
	private final int passwordResetExpirationMinutes;
	private final int maxLoginAttempts;
	private final long lockoutMinutes;

	public AuthServiceImpl(
			AppUserRepository appUserRepository,
			PasswordEncoder passwordEncoder,
			AuthMapper authMapper,
			JwtService jwtService,
			RefreshTokenRepository refreshTokenRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			@Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs,
			@Value("${password-reset.expiration-minutes}") int passwordResetExpirationMinutes,
			@Value("${security.login.max-attempts}") int maxLoginAttempts,
			@Value("${security.login.lockout-minutes}") long lockoutMinutes
	) {
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.authMapper = authMapper;
		this.jwtService = jwtService;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.refreshExpirationMs = refreshExpirationMs;
		this.passwordResetExpirationMinutes = passwordResetExpirationMinutes;
		this.maxLoginAttempts = maxLoginAttempts;
		this.lockoutMinutes = lockoutMinutes;
	}

	@Override
	@Transactional
	public AuthResponse login(LoginRequest request) {
		String normalizedEmail = request.getEmail().trim().toLowerCase();
		AppUser user = appUserRepository.findByEmail(normalizedEmail)
				.orElseThrow(() -> new IllegalArgumentException("Invalid credentials."));

		if (user.getLockedUntil() != null) {
			if (user.getLockedUntil().isAfter(LocalDateTime.now())) {
				throw new AccountLockedException(
						"Account locked until " + user.getLockedUntil() + " after too many failed login attempts.");
			}
			user.setLockedUntil(null);
			user.setFailedLoginAttempts(0);
		}

		if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
			registerFailedAttempt(user);
			throw new IllegalArgumentException("Invalid credentials.");
		}

		if (!user.isActive()) {
			throw new IllegalStateException("This account has been deactivated.");
		}

		user.setFailedLoginAttempts(0);
		appUserRepository.save(user);

		AuthResponse response = authMapper.toResponse(user, "Login successful.");
		response.setToken(jwtService.generateToken(user));
		response.setRefreshToken(issueRefreshToken(user).getToken());
		return response;
	}

	@Override
	@Transactional
	public AuthResponse refresh(RefreshRequest request) {
		RefreshToken existing = refreshTokenRepository.findByToken(request.getRefreshToken())
				.orElseThrow(() -> new IllegalArgumentException("Invalid refresh token."));

		if (!existing.isValid()) {
			throw new IllegalArgumentException("Refresh token expired or revoked.");
		}

		existing.setRevoked(true);
		refreshTokenRepository.save(existing);

		AppUser user = existing.getAppUser();
		AuthResponse response = authMapper.toResponse(user, "Token refreshed.");
		response.setToken(jwtService.generateToken(user));
		response.setRefreshToken(issueRefreshToken(user).getToken());
		return response;
	}

	@Override
	@Transactional
	public void forgotPassword(ForgotPasswordRequest request) {
		String normalizedEmail = request.getEmail().trim().toLowerCase();
		appUserRepository.findByEmail(normalizedEmail).ifPresent(user -> {
			PasswordResetToken resetToken = new PasswordResetToken();
			resetToken.setToken(UUID.randomUUID().toString());
			resetToken.setAppUser(user);
			resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(passwordResetExpirationMinutes));
			passwordResetTokenRepository.save(resetToken);

			// L'envoi d'e-mail (SMTP) n'est pas encore branché : le lien est journalisé
			// pour permettre les tests en attendant l'intégration d'un service d'envoi.
			log.info("Password reset requested for {}. Token (dev only): {}", user.getEmail(), resetToken.getToken());
		});
	}

	@Override
	@Transactional
	public void resetPassword(ResetPasswordRequest request) {
		PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
				.orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token."));

		if (!resetToken.isValid()) {
			throw new IllegalArgumentException("Invalid or expired reset token.");
		}

		AppUser user = resetToken.getAppUser();
		user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
		user.setFailedLoginAttempts(0);
		user.setLockedUntil(null);
		appUserRepository.save(user);

		resetToken.setUsed(true);
		passwordResetTokenRepository.save(resetToken);
	}

	private void registerFailedAttempt(AppUser user) {
		int attempts = user.getFailedLoginAttempts() + 1;
		user.setFailedLoginAttempts(attempts);
		if (attempts >= maxLoginAttempts) {
			user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
		}
		appUserRepository.save(user);
	}

	private RefreshToken issueRefreshToken(AppUser user) {
		RefreshToken refreshToken = new RefreshToken();
		refreshToken.setToken(UUID.randomUUID().toString());
		refreshToken.setAppUser(user);
		refreshToken.setExpiryDate(LocalDateTime.now().plus(Duration.ofMillis(refreshExpirationMs)));
		return refreshTokenRepository.save(refreshToken);
	}
}
