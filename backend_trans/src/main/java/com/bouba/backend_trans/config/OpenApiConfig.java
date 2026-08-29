package com.bouba.backend_trans.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Deux schemas d'authentification distincts coexistent sur l'API (§4.4, §6) :
 * un jeton JWT pour les postes de supervision, une cle API par equipement
 * pour les agents (en-tete X-API-Key, /api/v1/metrics/**). Documentes ici
 * pour que Swagger UI propose le bon type de justificatif selon l'endpoint.
 */
@Configuration
public class OpenApiConfig {

	private static final String JWT_SCHEME = "bearer-jwt";
	private static final String API_KEY_SCHEME = "api-key";

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Supervision EPT — API")
						.description("API de supervision du parc reseau et serveurs de l'EPT (PFE).")
						.version("v1"))
				.addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME))
				.components(new Components()
						.addSecuritySchemes(JWT_SCHEME, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("Jeton obtenu via POST /api/auth/login, poste de supervision."))
						.addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.HEADER)
								.name("X-API-Key")
								.description("Cle propre a l'equipement, reservee a /api/v1/metrics/**.")));
	}
}
