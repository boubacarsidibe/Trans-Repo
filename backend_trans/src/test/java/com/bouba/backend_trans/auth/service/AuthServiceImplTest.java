package com.bouba.backend_trans.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.bouba.backend_trans.auth.dto.AuthResponse;
import com.bouba.backend_trans.auth.dto.ForgotPasswordRequest;
import com.bouba.backend_trans.auth.dto.LoginRequest;
import com.bouba.backend_trans.auth.dto.RefreshRequest;
import com.bouba.backend_trans.auth.dto.ResetPasswordRequest;
import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.PasswordResetToken;
import com.bouba.backend_trans.auth.entity.RefreshToken;
import com.bouba.backend_trans.auth.entity.Role;
import com.bouba.backend_trans.auth.entity.UserType;
import com.bouba.backend_trans.auth.mapper.AuthMapper;
import com.bouba.backend_trans.auth.repository.AppUserRepository;
import com.bouba.backend_trans.auth.repository.PasswordResetTokenRepository;
import com.bouba.backend_trans.auth.repository.RefreshTokenRepository;
import com.bouba.backend_trans.auth.security.JwtService;
import com.bouba.backend_trans.exception.AccountLockedException;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

	private static final int MAX_TENTATIVES = 3;
	private static final long VERROUILLAGE_MINUTES = 15L;
	private static final long REFRESH_EXPIRATION_MS = 604_800_000L;
	private static final int RESET_EXPIRATION_MINUTES = 30;

	@Mock
	private AppUserRepository appUserRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Mock
	private PasswordResetTokenRepository passwordResetTokenRepository;

	private AuthServiceImpl authService;

	@BeforeEach
	void initService() {
		authService = new AuthServiceImpl(
				appUserRepository,
				passwordEncoder,
				new AuthMapper(),
				jwtService,
				refreshTokenRepository,
				passwordResetTokenRepository,
				REFRESH_EXPIRATION_MS,
				RESET_EXPIRATION_MINUTES,
				MAX_TENTATIVES,
				VERROUILLAGE_MINUTES);
	}

	// --- login ---

	@Test
	void connexion_reussie_retourne_les_jetons_et_reinitialise_les_tentatives() {
		AppUser user = utilisateur();
		user.setFailedLoginAttempts(2);
		when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("bonMotDePasse", user.getPasswordHash())).thenReturn(true);
		when(jwtService.generateToken(user)).thenReturn("jeton-acces");
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

		AuthResponse response = authService.login(requeteConnexion(user.getEmail(), "bonMotDePasse"));

		assertThat(response.getToken()).isEqualTo("jeton-acces");
		assertThat(response.getRefreshToken()).isNotBlank();
		assertThat(user.getFailedLoginAttempts()).isZero();
		verify(appUserRepository).save(user);
	}

	@Test
	void rejette_un_mot_de_passe_incorrect_et_incremente_les_tentatives_echouees() {
		AppUser user = utilisateur();
		when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("mauvaisMotDePasse", user.getPasswordHash())).thenReturn(false);

		assertThatThrownBy(() -> authService.login(requeteConnexion(user.getEmail(), "mauvaisMotDePasse")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Invalid credentials.");

		assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
		assertThat(user.getLockedUntil()).isNull();
		verify(appUserRepository).save(user);
	}

	@Test
	void rejette_un_email_inconnu() {
		when(appUserRepository.findByEmail("inconnu@ept.sn")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(requeteConnexion("inconnu@ept.sn", "peuImporte")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Invalid credentials.");
	}

	@Test
	void verrouille_le_compte_apres_le_nombre_maximal_de_tentatives_echouees() {
		AppUser user = utilisateur();
		user.setFailedLoginAttempts(MAX_TENTATIVES - 1);
		when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("mauvaisMotDePasse", user.getPasswordHash())).thenReturn(false);

		assertThatThrownBy(() -> authService.login(requeteConnexion(user.getEmail(), "mauvaisMotDePasse")))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(user.getFailedLoginAttempts()).isEqualTo(MAX_TENTATIVES);
		assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now());
	}

	@Test
	void rejette_la_connexion_meme_avec_le_bon_mot_de_passe_tant_que_le_compte_est_verrouille() {
		AppUser user = utilisateur();
		user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
		when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> authService.login(requeteConnexion(user.getEmail(), "bonMotDePasse")))
				.isInstanceOf(AccountLockedException.class);

		verify(passwordEncoder, never()).matches(any(), any());
	}

	@Test
	void deverrouille_le_compte_une_fois_le_verrouillage_expire() {
		AppUser user = utilisateur();
		user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
		user.setFailedLoginAttempts(MAX_TENTATIVES);
		when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("bonMotDePasse", user.getPasswordHash())).thenReturn(true);
		when(jwtService.generateToken(user)).thenReturn("jeton-acces");
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

		AuthResponse response = authService.login(requeteConnexion(user.getEmail(), "bonMotDePasse"));

		assertThat(response.getToken()).isEqualTo("jeton-acces");
		assertThat(user.getLockedUntil()).isNull();
		assertThat(user.getFailedLoginAttempts()).isZero();
	}

	@Test
	void rejette_la_connexion_d_un_compte_desactive() {
		AppUser user = utilisateur();
		user.setActive(false);
		when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("bonMotDePasse", user.getPasswordHash())).thenReturn(true);

		assertThatThrownBy(() -> authService.login(requeteConnexion(user.getEmail(), "bonMotDePasse")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("This account has been deactivated.");
	}

	// --- refresh ---

	@Test
	void rafraichit_le_jeton_d_acces_avec_un_refresh_token_valide_et_revoque_l_ancien() {
		AppUser user = utilisateur();
		RefreshToken ancien = jetonRafraichissement("ancien-refresh-token", user, false, LocalDateTime.now().plusDays(1));
		when(refreshTokenRepository.findByToken("ancien-refresh-token")).thenReturn(Optional.of(ancien));
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
		when(jwtService.generateToken(user)).thenReturn("nouveau-jeton-acces");

		AuthResponse response = authService.refresh(requeteRafraichissement("ancien-refresh-token"));

		assertThat(response.getToken()).isEqualTo("nouveau-jeton-acces");
		assertThat(response.getRefreshToken()).isNotBlank().isNotEqualTo("ancien-refresh-token");
		assertThat(ancien.isRevoked()).isTrue();
	}

	@Test
	void rejette_un_refresh_token_inconnu() {
		when(refreshTokenRepository.findByToken("inconnu")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.refresh(requeteRafraichissement("inconnu")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Invalid refresh token.");
	}

	@Test
	void rejette_un_refresh_token_revoque() {
		AppUser user = utilisateur();
		RefreshToken revoque = jetonRafraichissement("revoque", user, true, LocalDateTime.now().plusDays(1));
		when(refreshTokenRepository.findByToken("revoque")).thenReturn(Optional.of(revoque));

		assertThatThrownBy(() -> authService.refresh(requeteRafraichissement("revoque")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Refresh token expired or revoked.");
	}

	@Test
	void rejette_un_refresh_token_expire() {
		AppUser user = utilisateur();
		RefreshToken expire = jetonRafraichissement("expire", user, false, LocalDateTime.now().minusMinutes(1));
		when(refreshTokenRepository.findByToken("expire")).thenReturn(Optional.of(expire));

		assertThatThrownBy(() -> authService.refresh(requeteRafraichissement("expire")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Refresh token expired or revoked.");
	}

	// --- forgotPassword ---

	@Test
	void genere_un_jeton_de_reinitialisation_pour_un_email_connu() {
		AppUser user = utilisateur();
		when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

		ForgotPasswordRequest request = new ForgotPasswordRequest();
		request.setEmail(user.getEmail());
		authService.forgotPassword(request);

		ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
		verify(passwordResetTokenRepository).save(captor.capture());
		PasswordResetToken jeton = captor.getValue();
		assertThat(jeton.getToken()).isNotBlank();
		assertThat(jeton.getAppUser()).isEqualTo(user);
		assertThat(jeton.getExpiryDate()).isAfter(LocalDateTime.now().plusMinutes(RESET_EXPIRATION_MINUTES - 1));
	}

	@Test
	void ne_genere_aucun_jeton_pour_un_email_inconnu() {
		when(appUserRepository.findByEmail("inconnu@ept.sn")).thenReturn(Optional.empty());

		ForgotPasswordRequest request = new ForgotPasswordRequest();
		request.setEmail("inconnu@ept.sn");
		authService.forgotPassword(request);

		verify(passwordResetTokenRepository, never()).save(any());
	}

	@Test
	void ne_journalise_jamais_le_jeton_de_reinitialisation_au_niveau_info() {
		AppUser user = utilisateur();
		when(appUserRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

		Logger logger = (Logger) LoggerFactory.getLogger(AuthServiceImpl.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			ForgotPasswordRequest request = new ForgotPasswordRequest();
			request.setEmail(user.getEmail());
			authService.forgotPassword(request);

			ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
			verify(passwordResetTokenRepository).save(captor.capture());
			String jeton = captor.getValue().getToken();

			assertThat(appender.list)
					.filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.INFO))
					.extracting(ILoggingEvent::getFormattedMessage)
					.noneMatch(message -> message.contains(jeton));
		} finally {
			logger.detachAppender(appender);
		}
	}

	// --- resetPassword ---

	@Test
	void reinitialise_le_mot_de_passe_avec_un_jeton_valide() {
		AppUser user = utilisateur();
		user.setFailedLoginAttempts(2);
		user.setLockedUntil(LocalDateTime.now().plusMinutes(5));
		PasswordResetToken jeton = jetonReset("jeton-valide", user, false, LocalDateTime.now().plusMinutes(10));
		when(passwordResetTokenRepository.findByToken("jeton-valide")).thenReturn(Optional.of(jeton));
		when(passwordEncoder.encode("nouveauMotDePasse123")).thenReturn("nouveau-hash");

		authService.resetPassword(requeteReset("jeton-valide", "nouveauMotDePasse123"));

		assertThat(user.getPasswordHash()).isEqualTo("nouveau-hash");
		assertThat(user.getFailedLoginAttempts()).isZero();
		assertThat(user.getLockedUntil()).isNull();
		assertThat(jeton.isUsed()).isTrue();
		verify(appUserRepository).save(user);
		verify(passwordResetTokenRepository).save(jeton);
	}

	@Test
	void rejette_la_reinitialisation_avec_un_jeton_inconnu() {
		when(passwordResetTokenRepository.findByToken("inconnu")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.resetPassword(requeteReset("inconnu", "peuImporte123")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Invalid or expired reset token.");
	}

	@Test
	void rejette_la_reinitialisation_avec_un_jeton_deja_utilise() {
		AppUser user = utilisateur();
		PasswordResetToken jeton = jetonReset("deja-utilise", user, true, LocalDateTime.now().plusMinutes(10));
		when(passwordResetTokenRepository.findByToken("deja-utilise")).thenReturn(Optional.of(jeton));

		assertThatThrownBy(() -> authService.resetPassword(requeteReset("deja-utilise", "peuImporte123")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Invalid or expired reset token.");
	}

	@Test
	void rejette_la_reinitialisation_avec_un_jeton_expire() {
		AppUser user = utilisateur();
		PasswordResetToken jeton = jetonReset("expire", user, false, LocalDateTime.now().minusMinutes(1));
		when(passwordResetTokenRepository.findByToken("expire")).thenReturn(Optional.of(jeton));

		assertThatThrownBy(() -> authService.resetPassword(requeteReset("expire", "peuImporte123")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Invalid or expired reset token.");
	}

	// --- fixtures ---

	private AppUser utilisateur() {
		AppUser user = new AppUser();
		user.setId(1L);
		user.setUsername("Amina Diop");
		user.setEmail("amina.diop@ept.sn");
		user.setPasswordHash("hash-bcrypt");
		user.setRole(Role.OBSERVATEUR);
		user.setUserType(UserType.INDIVIDUAL);
		user.setActive(true);
		user.setFailedLoginAttempts(0);
		return user;
	}

	private LoginRequest requeteConnexion(String email, String motDePasse) {
		LoginRequest request = new LoginRequest();
		request.setEmail(email);
		request.setPassword(motDePasse);
		return request;
	}

	private RefreshRequest requeteRafraichissement(String refreshToken) {
		RefreshRequest request = new RefreshRequest();
		request.setRefreshToken(refreshToken);
		return request;
	}

	private ResetPasswordRequest requeteReset(String token, String nouveauMotDePasse) {
		ResetPasswordRequest request = new ResetPasswordRequest();
		request.setToken(token);
		request.setNewPassword(nouveauMotDePasse);
		return request;
	}

	private RefreshToken jetonRafraichissement(String token, AppUser user, boolean revoque, LocalDateTime expiration) {
		RefreshToken refreshToken = new RefreshToken();
		refreshToken.setToken(token);
		refreshToken.setAppUser(user);
		refreshToken.setRevoked(revoque);
		refreshToken.setExpiryDate(expiration);
		return refreshToken;
	}

	private PasswordResetToken jetonReset(String token, AppUser user, boolean utilise, LocalDateTime expiration) {
		PasswordResetToken resetToken = new PasswordResetToken();
		resetToken.setToken(token);
		resetToken.setAppUser(user);
		resetToken.setUsed(utilise);
		resetToken.setExpiryDate(expiration);
		return resetToken;
	}
}
