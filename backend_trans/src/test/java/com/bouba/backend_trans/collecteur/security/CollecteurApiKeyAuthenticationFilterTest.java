package com.bouba.backend_trans.collecteur.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;

class CollecteurApiKeyAuthenticationFilterTest {

	private static final String CLE_API = "cle-partagee-de-test";

	@BeforeEach
	@AfterEach
	void nettoyerLeContexteDeSecurite() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void authentifie_la_requete_pour_la_cle_partagee_configuree() throws Exception {
		CollecteurApiKeyAuthenticationFilter filter = new CollecteurApiKeyAuthenticationFilter(CLE_API);

		MockHttpServletRequest request = requeteHeartbeat();
		request.addHeader("X-Collector-Key", CLE_API);
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
				.extracting("authority")
				.containsExactly("ROLE_COLLECTOR");
	}

	@Test
	void rejette_une_cle_incorrecte() throws Exception {
		CollecteurApiKeyAuthenticationFilter filter = new CollecteurApiKeyAuthenticationFilter(CLE_API);

		MockHttpServletRequest request = requeteHeartbeat();
		request.addHeader("X-Collector-Key", "mauvaise-cle");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void rejette_toute_cle_quand_aucune_n_est_configuree_cote_backend() throws Exception {
		// Feature desactivee par defaut (app.collecteurs.cle-api vide) : la route
		// doit rester fermee meme si un appelant fournit une cle non vide.
		CollecteurApiKeyAuthenticationFilter filter = new CollecteurApiKeyAuthenticationFilter("");

		MockHttpServletRequest request = requeteHeartbeat();
		request.addHeader("X-Collector-Key", "peu-importe");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void laisse_passer_une_requete_de_heartbeat_sans_en_tete_de_cle() throws Exception {
		CollecteurApiKeyAuthenticationFilter filter = new CollecteurApiKeyAuthenticationFilter(CLE_API);

		MockHttpServletRequest request = requeteHeartbeat();
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void n_applique_aucun_controle_en_dehors_des_routes_de_collecteurs() throws Exception {
		CollecteurApiKeyAuthenticationFilter filter = new CollecteurApiKeyAuthenticationFilter(CLE_API);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/equipments");
		request.addHeader("X-Collector-Key", CLE_API);
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	private MockHttpServletRequest requeteHeartbeat() {
		return new MockHttpServletRequest("POST", "/api/v1/collectors/heartbeat");
	}
}
