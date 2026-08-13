package com.bouba.backend_trans.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Point d'entrée unique de la diffusion temps réel.
 *
 * <p>Les services métier publient un {@link EvenementSupervision} ; la diffusion
 * effective n'a lieu qu'<strong>après le commit</strong> de la transaction. Sans
 * cette précaution, l'ingestion d'un lot de métriques qui échoue en cours de
 * route aurait déjà notifié les consoles de valeurs jamais enregistrées.
 */
@Component
public class DiffusionSupervision {

	private static final Logger log = LoggerFactory.getLogger(DiffusionSupervision.class);

	private final SupervisionWebSocketHandler handler;
	private final ObjectMapper objectMapper;
	private final ApplicationEventPublisher publisher;

	public DiffusionSupervision(
			SupervisionWebSocketHandler handler,
			ObjectMapper objectMapper,
			ApplicationEventPublisher publisher
	) {
		this.handler = handler;
		this.objectMapper = objectMapper;
		this.publisher = publisher;
	}

	/**
	 * À appeler depuis la transaction métier, avec une charge utile déjà
	 * détachée de la session JPA.
	 */
	public void publier(TypeEvenement type, Object payload) {
		publisher.publishEvent(EvenementSupervision.de(type, payload));
	}

	/**
	 * {@code fallbackExecution} laisse passer les événements émis hors
	 * transaction, notamment ceux des tâches planifiées.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void surEvenement(EvenementSupervision evenement) {
		try {
			handler.diffuser(evenement.type().getCanal(), objectMapper.writeValueAsString(evenement));
		} catch (JacksonException ex) {
			// La supervision ne doit jamais faire tomber le traitement métier :
			// l'événement est perdu, la donnée reste en base et consultable.
			log.error("Sérialisation impossible pour l'événement {} : {}", evenement.type(), ex.getMessage());
		}
	}
}
