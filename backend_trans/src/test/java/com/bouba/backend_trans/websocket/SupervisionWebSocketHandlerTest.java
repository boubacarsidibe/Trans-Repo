package com.bouba.backend_trans.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Registre des consoles connectées, canal par canal (§8.2), et diffusion des
 * événements de supervision.
 */
class SupervisionWebSocketHandlerTest {

	private SupervisionWebSocketHandler handler;

	@BeforeEach
	void initHandler() {
		handler = new SupervisionWebSocketHandler(1000, 1_000_000);
	}

	@Test
	void rattache_une_session_au_canal_correspondant_a_son_chemin() throws Exception {
		WebSocketSession session = sessionOuverte("id-1", "/ws/metrics");

		handler.afterConnectionEstablished(session);

		assertThat(handler.nombreDeSessions(CanalSupervision.METRICS)).isEqualTo(1);
		assertThat(handler.nombreDeSessions(CanalSupervision.ALERTS)).isZero();
		assertThat(handler.nombreTotalDeSessions()).isEqualTo(1);
	}

	@Test
	void refuse_une_session_dont_le_chemin_ne_correspond_a_aucun_canal() throws Exception {
		WebSocketSession session = sessionOuverte("id-1", "/ws/inconnu");

		handler.afterConnectionEstablished(session);

		verify(session).close(CloseStatus.NOT_ACCEPTABLE);
		assertThat(handler.nombreTotalDeSessions()).isZero();
	}

	@Test
	void retire_la_session_a_la_fermeture_de_connexion() throws Exception {
		WebSocketSession session = sessionOuverte("id-1", "/ws/alerts");
		handler.afterConnectionEstablished(session);

		handler.afterConnectionClosed(session, CloseStatus.NORMAL);

		assertThat(handler.nombreDeSessions(CanalSupervision.ALERTS)).isZero();
	}

	@Test
	void retire_la_session_apres_une_erreur_de_transport() throws Exception {
		WebSocketSession session = sessionOuverte("id-1", "/ws/status");
		handler.afterConnectionEstablished(session);

		handler.handleTransportError(session, new IOException("connexion perdue"));

		assertThat(handler.nombreDeSessions(CanalSupervision.STATUS)).isZero();
	}

	@Test
	void diffuse_le_message_a_toutes_les_sessions_ouvertes_du_canal() throws Exception {
		WebSocketSession session1 = sessionOuverte("id-1", "/ws/metrics");
		WebSocketSession session2 = sessionOuverte("id-2", "/ws/metrics");
		handler.afterConnectionEstablished(session1);
		handler.afterConnectionEstablished(session2);

		handler.diffuser(CanalSupervision.METRICS, "{\"type\":\"metric_update\"}");

		verify(session1).sendMessage(new TextMessage("{\"type\":\"metric_update\"}"));
		verify(session2).sendMessage(new TextMessage("{\"type\":\"metric_update\"}"));
	}

	@Test
	void ne_diffuse_pas_sur_les_autres_canaux() throws Exception {
		WebSocketSession session = sessionOuverte("id-1", "/ws/alerts");
		handler.afterConnectionEstablished(session);

		handler.diffuser(CanalSupervision.METRICS, "{\"type\":\"metric_update\"}");

		verify(session, never()).sendMessage(any());
	}

	@Test
	void retire_une_session_fermee_sans_interrompre_la_diffusion_aux_autres() throws Exception {
		WebSocketSession sessionFermee = sessionOuverte("id-1", "/ws/metrics");
		WebSocketSession sessionOuverte = sessionOuverte("id-2", "/ws/metrics");
		handler.afterConnectionEstablished(sessionFermee);
		handler.afterConnectionEstablished(sessionOuverte);
		when(sessionFermee.isOpen()).thenReturn(false);

		handler.diffuser(CanalSupervision.METRICS, "{}");

		verify(sessionFermee, never()).sendMessage(any());
		verify(sessionOuverte).sendMessage(new TextMessage("{}"));
		assertThat(handler.nombreDeSessions(CanalSupervision.METRICS)).isEqualTo(1);
	}

	@Test
	void retire_une_session_dont_l_envoi_echoue() throws Exception {
		WebSocketSession sessionDefaillante = sessionOuverte("id-1", "/ws/metrics");
		handler.afterConnectionEstablished(sessionDefaillante);
		doThrow(new IOException("tuyau rompu")).when(sessionDefaillante).sendMessage(any());

		handler.diffuser(CanalSupervision.METRICS, "{}");

		assertThat(handler.nombreDeSessions(CanalSupervision.METRICS)).isZero();
	}

	private WebSocketSession sessionOuverte(String id, String chemin) throws IOException {
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn(id);
		when(session.getUri()).thenReturn(URI.create("ws://localhost" + chemin));
		when(session.isOpen()).thenReturn(true);
		return session;
	}
}
