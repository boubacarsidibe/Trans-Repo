package com.bouba.backend_trans.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.Role;
import com.bouba.backend_trans.auth.repository.AppUserRepository;
import com.bouba.backend_trans.auth.security.JwtService;

import io.jsonwebtoken.JwtException;

/**
 * Le handshake WebSocket porte lui-même le contrôle du jeton (§8.4) : une
 * connexion non authentifiée doit être rejetée avant même l'ouverture du
 * canal, pas laissée passer pour être filtrée plus tard.
 */
class JwtHandshakeInterceptorTest {

	private static final String JETON = "jeton.de.test";

	private final JwtService jwtService = mock(JwtService.class);
	private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
	private final JwtHandshakeInterceptor interceptor =
			new JwtHandshakeInterceptor(jwtService, appUserRepository);
	private final WebSocketHandler wsHandler = mock(WebSocketHandler.class);

	@Test
	void accepte_une_connexion_avec_un_jeton_valide_dans_l_en_tete() {
		when(jwtService.extractUsername(JETON)).thenReturn("marie@exemple.sn");
		when(appUserRepository.findByEmail("marie@exemple.sn")).thenReturn(Optional.of(utilisateur(true)));
		ServerHttpRequest request = requeteAvecEnTete(JETON);
		ServerHttpResponse response = mock(ServerHttpResponse.class);
		Map<String, Object> attributs = new HashMap<>();

		boolean accepte = interceptor.beforeHandshake(request, response, wsHandler, attributs);

		assertThat(accepte).isTrue();
		assertThat(attributs).containsEntry(JwtHandshakeInterceptor.ATTR_EMAIL, "marie@exemple.sn");
		assertThat(attributs).containsEntry(JwtHandshakeInterceptor.ATTR_ROLE, Role.TECHNICIEN.name());
	}

	@Test
	void accepte_une_connexion_avec_un_jeton_valide_en_parametre_de_requete() {
		when(jwtService.extractUsername(JETON)).thenReturn("marie@exemple.sn");
		when(appUserRepository.findByEmail("marie@exemple.sn")).thenReturn(Optional.of(utilisateur(true)));
		ServerHttpRequest request = requeteAvecParametre(JETON);
		ServerHttpResponse response = mock(ServerHttpResponse.class);

		boolean accepte = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

		assertThat(accepte).isTrue();
	}

	@Test
	void refuse_une_connexion_sans_jeton() {
		ServerHttpRequest request = requeteSansJeton();
		ServerHttpResponse response = mock(ServerHttpResponse.class);

		boolean accepte = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

		assertThat(accepte).isFalse();
		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void refuse_une_connexion_avec_un_jeton_expire_ou_invalide() {
		when(jwtService.extractUsername(JETON)).thenThrow(new JwtException("jeton expiré"));
		ServerHttpRequest request = requeteAvecEnTete(JETON);
		ServerHttpResponse response = mock(ServerHttpResponse.class);

		boolean accepte = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

		assertThat(accepte).isFalse();
		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void refuse_une_connexion_pour_un_compte_inconnu() {
		when(jwtService.extractUsername(JETON)).thenReturn("fantome@exemple.sn");
		when(appUserRepository.findByEmail("fantome@exemple.sn")).thenReturn(Optional.empty());
		ServerHttpRequest request = requeteAvecEnTete(JETON);
		ServerHttpResponse response = mock(ServerHttpResponse.class);

		boolean accepte = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

		assertThat(accepte).isFalse();
		verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void refuse_une_connexion_pour_un_compte_desactive() {
		when(jwtService.extractUsername(JETON)).thenReturn("marie@exemple.sn");
		when(appUserRepository.findByEmail("marie@exemple.sn")).thenReturn(Optional.of(utilisateur(false)));
		ServerHttpRequest request = requeteAvecEnTete(JETON);
		ServerHttpResponse response = mock(ServerHttpResponse.class);

		boolean accepte = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

		assertThat(accepte).isFalse();
	}

	private ServerHttpRequest requeteAvecEnTete(String jeton) {
		MockHttpServletRequest requeteServlet = new MockHttpServletRequest("GET", "/ws/metrics");
		requeteServlet.addHeader("Authorization", "Bearer " + jeton);
		return new ServletServerHttpRequest(requeteServlet);
	}

	private ServerHttpRequest requeteAvecParametre(String jeton) {
		MockHttpServletRequest requeteServlet = new MockHttpServletRequest("GET", "/ws/metrics");
		requeteServlet.setParameter("token", jeton);
		return new ServletServerHttpRequest(requeteServlet);
	}

	private ServerHttpRequest requeteSansJeton() {
		return new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/ws/metrics"));
	}

	private AppUser utilisateur(boolean actif) {
		AppUser utilisateur = new AppUser();
		utilisateur.setId(1L);
		utilisateur.setUsername("marie");
		utilisateur.setEmail("marie@exemple.sn");
		utilisateur.setRole(Role.TECHNICIEN);
		utilisateur.setActive(actif);
		return utilisateur;
	}
}
