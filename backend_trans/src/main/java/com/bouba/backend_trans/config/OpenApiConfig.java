package com.bouba.backend_trans.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Métadonnées OpenAPI + bouton "Authorize" (JWT) pour /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

	private static final String SCHEME_JWT = "bearer-jwt";

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("API de supervision — EPT")
						.description("Authentification, équipements, métriques, alertes, rapports (F1-F8).")
						.version("v1"))
				.addSecurityItem(new SecurityRequirement().addList(SCHEME_JWT))
				.components(new Components()
						.addSecuritySchemes(SCHEME_JWT, new SecurityScheme()
								.name(SCHEME_JWT)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")));
	}
}
