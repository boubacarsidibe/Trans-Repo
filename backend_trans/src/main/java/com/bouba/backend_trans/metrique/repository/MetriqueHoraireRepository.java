package com.bouba.backend_trans.metrique.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bouba.backend_trans.metrique.entity.MetriqueHoraire;

public interface MetriqueHoraireRepository extends JpaRepository<MetriqueHoraire, Long> {

	/**
	 * Replie les mesures brutes antérieures à la limite en moyennes horaires.
	 *
	 * <p>Écrit en une seule instruction côté base : ramener des dizaines de
	 * millions de lignes dans la JVM pour les moyenner serait intenable. Le
	 * {@code ON CONFLICT} rend l'opération rejouable — un lot déjà replié n'est
	 * pas dupliqué si la tâche est relancée.
	 */
	@Modifying
	@Query(value = """
			INSERT INTO metriques_horaires
				(equipement_id, type_metrique, heure, moyenne, minimum, maximum, nombre_mesures)
			SELECT equipement_id,
			       type_metrique,
			       date_trunc('hour', horodatage),
			       AVG(valeur),
			       MIN(valeur),
			       MAX(valeur),
			       COUNT(*)
			FROM metriques
			WHERE horodatage < :limite
			GROUP BY equipement_id, type_metrique, date_trunc('hour', horodatage)
			ON CONFLICT (equipement_id, type_metrique, heure) DO NOTHING
			""", nativeQuery = true)
	int replierEnHeures(@Param("limite") LocalDateTime limite);

	@Modifying
	@Query(value = "DELETE FROM metriques WHERE horodatage < :limite", nativeQuery = true)
	int supprimerBrutesAvant(@Param("limite") LocalDateTime limite);
}
