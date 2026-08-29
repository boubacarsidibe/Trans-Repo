package com.bouba.backend_trans.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.Role;
import com.bouba.backend_trans.auth.entity.UserType;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;

class JwtServiceTest {

	private static final String SECRET = "Y2xlLXNlY3JldGUtZGUtdGVzdC1wb3VyLWp3dC0wMTIzNDU2Nzg5QUJDREVG";

	private final JwtService jwtService = new JwtService(SECRET, 900_000L);

	@Test
	void genere_un_jeton_dont_le_sujet_est_l_email_de_l_utilisateur() {
		AppUser user = utilisateur();

		String token = jwtService.generateToken(user);

		assertThat(token).isNotBlank();
		assertThat(jwtService.extractUsername(token)).isEqualTo(user.getEmail());
	}

	@Test
	void valide_un_jeton_dont_le_sujet_correspond_a_l_utilisateur_connecte() {
		AppUser user = utilisateur();
		String token = jwtService.generateToken(user);

		assertThat(jwtService.isTokenValid(token, utilisateurDetails(user))).isTrue();
	}

	@Test
	void invalide_un_jeton_dont_le_sujet_ne_correspond_pas_a_l_utilisateur_connecte() {
		String token = jwtService.generateToken(utilisateur());
		UserDetails autreUtilisateur = User.withUsername("autre@ept.sn")
				.password("hash")
				.authorities("ROLE_OBSERVATEUR")
				.build();

		assertThat(jwtService.isTokenValid(token, autreUtilisateur)).isFalse();
	}

	@Test
	void rejette_un_jeton_expire() {
		JwtService jwtServiceExpire = new JwtService(SECRET, -10_000L);
		String tokenExpire = jwtServiceExpire.generateToken(utilisateur());

		assertThatThrownBy(() -> jwtService.extractUsername(tokenExpire))
				.isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	void rejette_un_jeton_dont_la_signature_a_ete_alteree() {
		String token = jwtService.generateToken(utilisateur());
		int dernierPoint = token.lastIndexOf('.');
		String signatureInversee = new StringBuilder(token.substring(dernierPoint + 1)).reverse().toString();
		String tokenAltere = token.substring(0, dernierPoint + 1) + signatureInversee;

		assertThatThrownBy(() -> jwtService.extractUsername(tokenAltere))
				.isInstanceOf(SignatureException.class);
	}

	@Test
	void rejette_une_chaine_qui_n_est_pas_un_jeton_jwt() {
		assertThatThrownBy(() -> jwtService.extractUsername("ceci-nest-pas-un-jwt"))
				.isInstanceOf(MalformedJwtException.class);
	}

	private AppUser utilisateur() {
		AppUser user = new AppUser();
		user.setUsername("Amina Diop");
		user.setEmail("amina.diop@ept.sn");
		user.setPasswordHash("hash");
		user.setRole(Role.TECHNICIEN);
		user.setUserType(UserType.INDIVIDUAL);
		return user;
	}

	private UserDetails utilisateurDetails(AppUser user) {
		return User.withUsername(user.getEmail())
				.password(user.getPasswordHash())
				.authorities("ROLE_" + user.getRole().name())
				.build();
	}
}
