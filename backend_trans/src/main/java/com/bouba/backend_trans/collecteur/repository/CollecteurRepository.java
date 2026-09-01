package com.bouba.backend_trans.collecteur.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bouba.backend_trans.collecteur.entity.Collecteur;

public interface CollecteurRepository extends JpaRepository<Collecteur, String> {

	/** Les autres instances à désactiver quand une nouvelle se déclare active. */
	List<Collecteur> findByActifTrueAndCollecteurIdNot(String collecteurId);

	/**
	 * L'instance actuellement active, celle que {@code CollecteurWatchdog}
	 * doit surveiller. {@code orderByDernierHeartbeatDesc} ne sert qu'à choisir
	 * de façon déterministe en cas d'incohérence transitoire (deux lignes
	 * actives simultanément, le temps qu'une bascule se propage).
	 */
	Optional<Collecteur> findFirstByActifTrueOrderByDernierHeartbeatDesc();
}
