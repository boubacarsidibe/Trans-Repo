package com.bouba.backend_trans.config;

import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.bouba.backend_trans.auth.security.AppUserDetailsService;
import com.bouba.backend_trans.auth.security.JwtService;
import com.bouba.backend_trans.equipement.repository.EquipementRepository;

/**
 * Active la sécurité au niveau méthode (@PreAuthorize) pour les tests de
 * contrôleur en tranche @WebMvcTest. {@link SecurityConfig} est auto-détectée
 * par ces slices (les filtres qu'elle assemble sont des {@code @Component}
 * de type {@code Filter}) ; ses dépendances sont ici de simples doublures,
 * inertes tant qu'aucun en-tête d'authentification n'est envoyé — ce qui
 * suffit à exercer le vrai {@link SecurityConfig} pour les tests de rôle
 * (§4.4), authentifiés via {@code @WithMockUser}.
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
public class MethodSecurityTestConfig {

	@Bean
	public JwtService jwtService() {
		return mock(JwtService.class);
	}

	@Bean
	public AppUserDetailsService appUserDetailsService() {
		return mock(AppUserDetailsService.class);
	}

	@Bean
	public EquipementRepository equipementRepository() {
		return mock(EquipementRepository.class);
	}

	/**
	 * Chaîne de filtres minimale, pour que le refus d'une autorisation de
	 * méthode (@PreAuthorize) traverse {@code ExceptionTranslationFilter} et
	 * ressorte en 403 plutôt qu'en exception non gérée ; l'autorisation
	 * elle-même reste entièrement portée par la sécurité de méthode testée
	 * ici, pas par cette chaîne (tout est laissé passant au niveau web).
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		return http.build();
	}
}
