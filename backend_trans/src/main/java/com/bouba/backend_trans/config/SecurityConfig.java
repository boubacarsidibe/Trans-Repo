package com.bouba.backend_trans.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.bouba.backend_trans.auth.security.JwtAuthenticationFilter;
import com.bouba.backend_trans.equipement.security.ApiKeyAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
	private final List<String> corsAllowedOrigins;

	public SecurityConfig(
			JwtAuthenticationFilter jwtAuthenticationFilter,
			ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
			@Value("${app.cors.allowed-origins}") List<String> corsAllowedOrigins
	) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
		this.corsAllowedOrigins = corsAllowedOrigins;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/auth/**").permitAll()
						.requestMatchers("/api/v1/health").permitAll()
						// Le handshake porte lui-même le contrôle du jeton (§8.4) :
						// laisser passer ici, refuser dans JwtHandshakeInterceptor.
						.requestMatchers("/ws/**").permitAll()
						.requestMatchers("/api/v1/metrics/**").hasRole("AGENT")
						// Documentation de l'API (#43) : jamais exposee anonymement.
						.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
							.hasRole("ADMINISTRATEUR")
						.anyRequest().authenticated()
				)
				.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(corsAllowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
