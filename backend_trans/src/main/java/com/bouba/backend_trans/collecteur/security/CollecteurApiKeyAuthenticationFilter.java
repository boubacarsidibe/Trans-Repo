package com.bouba.backend_trans.collecteur.security;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authentifie les instances du collecteur réseau sur {@code /api/v1/collectors/**}
 * (issue #157).
 *
 * <p>Contrairement à {@code ApiKeyAuthenticationFilter} (une clé par
 * équipement, en base), les instances du collecteur partagent une seule clé
 * statique configurée côté backend ({@code app.collecteurs.cle-api}) : il n'y
 * a que deux instances (primaire/secondaire) et pas de fiche à administrer
 * pour chacune — cohérent avec le périmètre minimal demandé par l'issue.
 * Non configurée (valeur vide), la clé ne correspond jamais à rien et la
 * route reste fermée par défaut, comme les notifications e-mail (F7).
 */
@Component
public class CollecteurApiKeyAuthenticationFilter extends OncePerRequestFilter {

	private static final String API_KEY_HEADER = "X-Collector-Key";
	private static final String COLLECTORS_PATH_PREFIX = "/api/v1/collectors/";

	private final String cleApiConfiguree;

	public CollecteurApiKeyAuthenticationFilter(@Value("${app.collecteurs.cle-api}") String cleApiConfiguree) {
		this.cleApiConfiguree = cleApiConfiguree;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		if (!request.getRequestURI().startsWith(COLLECTORS_PATH_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		String apiKey = request.getHeader(API_KEY_HEADER);
		if (apiKey != null && !apiKey.isBlank() && !cleApiConfiguree.isBlank() && apiKey.equals(cleApiConfiguree)) {
			UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
					"collecteur-reseau",
					null,
					List.of(new SimpleGrantedAuthority("ROLE_COLLECTOR")));
			authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authToken);
		}

		filterChain.doFilter(request, response);
	}
}
