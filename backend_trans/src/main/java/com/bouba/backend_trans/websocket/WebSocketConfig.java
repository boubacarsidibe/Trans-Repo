package com.bouba.backend_trans.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Expose les trois canaux temps réel du §8.2, tous protégés par le même
 * contrôle de jeton à l'ouverture.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final SupervisionWebSocketHandler handler;
	private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
	private final String[] originesAutorisees;

	public WebSocketConfig(
			SupervisionWebSocketHandler handler,
			JwtHandshakeInterceptor jwtHandshakeInterceptor,
			@Value("${app.cors.allowed-origins}") String[] originesAutorisees
	) {
		this.handler = handler;
		this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
		this.originesAutorisees = originesAutorisees;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(
						handler,
						CanalSupervision.METRICS.getChemin(),
						CanalSupervision.ALERTS.getChemin(),
						CanalSupervision.STATUS.getChemin())
				.addInterceptors(jwtHandshakeInterceptor)
				.setAllowedOrigins(originesAutorisees);
	}
}
