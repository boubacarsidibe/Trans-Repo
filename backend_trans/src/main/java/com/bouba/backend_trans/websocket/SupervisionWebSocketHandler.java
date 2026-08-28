package com.bouba.backend_trans.websocket;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Tient le registre des consoles connectées, canal par canal, et leur pousse les
 * événements de supervision.
 *
 * <p>Le poste de supervision ne fait qu'écouter : aucun message entrant n'est
 * traité, toute action passe par l'API REST (§5.6).
 */
@Component
public class SupervisionWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(SupervisionWebSocketHandler.class);

	private final Map<CanalSupervision, Map<String, WebSocketSession>> sessionsParCanal =
			new EnumMap<>(CanalSupervision.class);

	private final int sendTimeLimitMs;
	private final int bufferSizeLimitBytes;

	public SupervisionWebSocketHandler(
			@Value("${app.websocket.send-time-limit-ms}") int sendTimeLimitMs,
			@Value("${app.websocket.buffer-size-limit-bytes}") int bufferSizeLimitBytes
	) {
		this.sendTimeLimitMs = sendTimeLimitMs;
		this.bufferSizeLimitBytes = bufferSizeLimitBytes;
		for (CanalSupervision canal : CanalSupervision.values()) {
			sessionsParCanal.put(canal, new ConcurrentHashMap<>());
		}
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		CanalSupervision canal = canalDe(session);
		if (canal == null) {
			session.close(CloseStatus.NOT_ACCEPTABLE);
			return;
		}

		// Le décorateur sérialise les envois concurrents : un même canal est
		// alimenté par l'ingestion de métriques et par le moteur d'alertes, qui
		// tournent sur des threads différents.
		sessionsParCanal.get(canal).put(
				session.getId(),
				new ConcurrentWebSocketSessionDecorator(session, sendTimeLimitMs, bufferSizeLimitBytes));

		log.debug("Console connectée au canal {} ({} session(s))", canal.getChemin(), nombreDeSessions(canal));
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		retirer(session);
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		log.warn("Erreur de transport WebSocket, session fermée : {}", exception.getMessage());
		retirer(session);
	}

	/**
	 * Pousse un message à toutes les consoles abonnées au canal. Une session
	 * défaillante est retirée du registre sans interrompre la diffusion aux
	 * autres.
	 */
	public void diffuser(CanalSupervision canal, String messageJson) {
		TextMessage message = new TextMessage(messageJson);

		sessionsParCanal.get(canal).forEach((id, session) -> {
			if (!session.isOpen()) {
				sessionsParCanal.get(canal).remove(id);
				return;
			}
			try {
				session.sendMessage(message);
			} catch (IOException | IllegalStateException ex) {
				log.warn("Diffusion impossible sur {} : {}", canal.getChemin(), ex.getMessage());
				sessionsParCanal.get(canal).remove(id);
			}
		});
	}

	public int nombreDeSessions(CanalSupervision canal) {
		return sessionsParCanal.get(canal).size();
	}

	public int nombreTotalDeSessions() {
		return sessionsParCanal.values().stream().mapToInt(sessions -> sessions.size()).sum();
	}

	private void retirer(WebSocketSession session) {
		sessionsParCanal.values().forEach(sessions -> sessions.remove(session.getId()));
	}

	private CanalSupervision canalDe(WebSocketSession session) {
		return session.getUri() == null ? null : CanalSupervision.parChemin(session.getUri().getPath());
	}
}
