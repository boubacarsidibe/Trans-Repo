package com.bouba.backend_trans.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import com.bouba.backend_trans.auth.entity.AppUser;
import com.bouba.backend_trans.auth.entity.Role;
import com.bouba.backend_trans.auth.entity.UserType;

import jakarta.servlet.FilterChain;

class JwtAuthenticationFilterTest {

	private static final String SECRET = "Y2xlLXNlY3JldGUtZGUtdGVzdC1wb3VyLWp3dC0wMTIzNDU2Nzg5QUJDREVG";

	private final AppUserDetailsService appUserDetailsService = mock(AppUserDetailsService.class);
	private final JwtService jwtService = new JwtService(SECRET, 900_000L);
	private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, appUserDetailsService);

	@BeforeEach
	@AfterEach
	void nettoyerLeContexteDeSecurite() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void laisse_passer_une_requete_sans_en_tete_authorization() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void ne_authentifie_pas_une_requete_portant_un_jeton_expire() throws Exception {
		JwtService jwtServiceExpire = new JwtService(SECRET, -10_000L);
		String tokenExpire = jwtServiceExpire.generateToken(utilisateur());

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + tokenExpire);
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void ne_authentifie_pas_une_requete_portant_un_jeton_invalide() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer jeton-invalide-mal-forme");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void authentifie_la_requete_pour_un_jeton_valide() throws Exception {
		AppUser user = utilisateur();
		String token = jwtService.generateToken(user);
		UserDetails userDetails = User.withUsername(user.getEmail())
				.password(user.getPasswordHash())
				.authorities("ROLE_" + user.getRole().name())
				.build();
		when(appUserDetailsService.loadUserByUsername(user.getEmail())).thenReturn(userDetails);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(user.getEmail());
	}

	private AppUser utilisateur() {
		AppUser user = new AppUser();
		user.setUsername("Amina Diop");
		user.setEmail("amina.diop@ept.sn");
		user.setPasswordHash("hash");
		user.setRole(Role.OBSERVATEUR);
		user.setUserType(UserType.INDIVIDUAL);
		return user;
	}
}
