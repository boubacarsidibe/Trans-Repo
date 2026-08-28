package com.bouba.backend_trans.websocket;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.repository.AppUserRepository;
import com.bouba.backend_trans.auth.security.JwtService;

import io.jsonwebtoken.JwtException;

/**
 * Valide le jeton JWT pendant la phase de handshake : une connexion non
 * authentifiée est rejetée immédiatement par le serveur (§8.4).
 *
 * <p>Le jeton est lu depuis l'en-tête {@code Authorization} quand le client peut
 * en poser un, sinon depuis le paramètre de requête {@code token} — l'API
 * WebSocket des navigateurs ne permet pas d'ajouter d'en-tête à l'ouverture.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

	public static final String ATTR_EMAIL = "email";
	public static final String ATTR_ROLE = "role";

	private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);
	private static final String PREFIXE_BEARER = "Bearer ";

	private final JwtService jwtService;
	private final AppUserRepository appUserRepository;

	public JwtHandshakeInterceptor(JwtService jwtService, AppUserRepository appUserRepository) {
		this.jwtService = jwtService;
		this.appUserRepository = appUserRepository;
	}

	@Override
	public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes
	) {
		String jeton = resoudreJeton(request);
		if (jeton == null) {
			return refuser(response, "jeton absent");
		}

		try {
			String email = jwtService.extractUsername(jeton);
			AppUser utilisateur = appUserRepository.findByEmail(email).orElse(null);

			if (utilisateur == null || !utilisateur.isActive()) {
				return refuser(response, "compte inconnu ou désactivé");
			}

			attributes.put(ATTR_EMAIL, email);
			attributes.put(ATTR_ROLE, utilisateur.getRole().name());
			return true;
		} catch (JwtException | IllegalArgumentException ex) {
			// Signature invalide ou jeton expiré : jjwt le signale à l'analyse.
			return refuser(response, "jeton invalide");
		}
	}

	@Override
	public void afterHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Exception exception
	) {
		// Rien à faire : le registre des sessions est tenu par le gestionnaire.
	}

	private String resoudreJeton(ServerHttpRequest request) {
		String entete = request.getHeaders().getFirst("Authorization");
		if (entete != null && entete.startsWith(PREFIXE_BEARER)) {
			return entete.substring(PREFIXE_BEARER.length()).trim();
		}

		if (request instanceof ServletServerHttpRequest servletRequest) {
			String parametre = servletRequest.getServletRequest().getParameter("token");
			if (parametre != null && !parametre.isBlank()) {
				return parametre;
			}
		}

		String depuisUri = UriComponentsBuilder.fromUri(request.getURI())
				.build()
				.getQueryParams()
				.getFirst("token");
		return depuisUri == null || depuisUri.isBlank() ? null : depuisUri;
	}

	private boolean refuser(ServerHttpResponse response, String motif) {
		log.debug("Handshake WebSocket refusé : {}", motif);
		response.setStatusCode(HttpStatus.UNAUTHORIZED);
		return false;
	}
}
