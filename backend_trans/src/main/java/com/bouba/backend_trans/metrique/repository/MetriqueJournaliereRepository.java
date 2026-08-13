package com.bouba.backend_trans.metrique.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bouba.backend_trans.metrique.entity.MetriqueJournaliere;

public interface MetriqueJournaliereRepository extends JpaRepository<MetriqueJournaliere, Long> {

	/**
	 * Replie les moyennes horaires trop anciennes en agrégats journaliers.
	 *
	 * <p>La moyenne des moyennes est pondérée par le nombre de mesures de chaque
	 * heure : une heure de collecte incomplète ne doit pas peser autant qu'une
	 * heure pleine.
	 */
	@Modifying
	@Query(value = """
			INSERT INTO metriques_journalieres
				(equipement_id, type_metrique, jour, moyenne, minimum, maximum, nombre_mesures)
			SELECT equipement_id,
			       type_metrique,
			       CAST(heure AS date),
			       SUM(moyenne * nombre_mesures) / NULLIF(SUM(nombre_mesures), 0),
			       MIN(minimum),
			       MAX(maximum),
			       SUM(nombre_mesures)
			FROM metriques_horaires
			WHERE heure < :limite
			GROUP BY equipement_id, type_metrique, CAST(heure AS date)
			ON CONFLICT (equipement_id, type_metrique, jour) DO NOTHING
			""", nativeQuery = true)
	int replierEnJours(@Param("limite") LocalDateTime limite);

	@Modifying
	@Query(value = "DELETE FROM metriques_horaires WHERE heure < :limite", nativeQuery = true)
	int supprimerHorairesAvant(@Param("limite") LocalDateTime limite);
}
