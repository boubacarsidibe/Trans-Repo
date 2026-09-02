package com.bouba.backend_trans.collecteur.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Instance du collecteur réseau (agent/network/network_collector.py) qui
 * s'est déjà signalée par heartbeat (issue #157 — redondance simple :
 * deux instances, une seule active à la fois).
 *
 * <p>Contrairement à {@code Equipement}, il n'y a pas de fiche créée à
 * l'avance côté administration : la ligne est créée au premier heartbeat
 * reçu, identifiée par le {@code COLLECTOR_ID} que l'instance déclare
 * elle-même dans sa configuration.
 */
@Entity
@Table(name = "collecteurs")
public class Collecteur {

	@Id
	@Column(name = "collecteur_id", length = 100)
	private String collecteurId;

	/**
	 * True pour l'instance qui sonde effectivement les équipements au moment
	 * du dernier heartbeat. À l'instant T, au plus une ligne devrait porter
	 * {@code actif = true} — {@code CollecteurService} désactive les autres
	 * quand une nouvelle instance se déclare active (bascule primaire/secondaire).
	 */
	@Column(nullable = false)
	private boolean actif;

	/**
	 * Horodatage du dernier heartbeat reçu, tenu à jour à chaque appel de
	 * {@code POST /api/v1/collectors/heartbeat}. C'est ce que lit
	 * {@code CollecteurWatchdog} pour détecter l'arrêt du collecteur actif,
	 * au même titre que {@code Equipement.derniereMesure} pour F3/F4.
	 */
	@Column(name = "dernier_heartbeat")
	private LocalDateTime dernierHeartbeat;

	public String getCollecteurId() {
		return collecteurId;
	}

	public void setCollecteurId(String collecteurId) {
		this.collecteurId = collecteurId;
	}

	public boolean isActif() {
		return actif;
	}

	public void setActif(boolean actif) {
		this.actif = actif;
	}

	public LocalDateTime getDernierHeartbeat() {
		return dernierHeartbeat;
	}

	public void setDernierHeartbeat(LocalDateTime dernierHeartbeat) {
		this.dernierHeartbeat = dernierHeartbeat;
	}
}
