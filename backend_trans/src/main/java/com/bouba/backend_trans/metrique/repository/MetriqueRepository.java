package com.bouba.backend_trans.metrique.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bouba.backend_trans.metrique.entity.Metrique;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;

public interface MetriqueRepository extends JpaRepository<Metrique, Long> {

	/**
	 * Historique borné dans le temps et paginé (§7.9).
	 *
	 * <p>La variante sans borne a été retirée : avec 90 jours de rétention et une
	 * mesure par minute, un seul équipement totalise plusieurs millions de
	 * lignes — les charger d'un bloc mettait le serveur à genoux.
	 */
	@EntityGraph(attributePaths = "equipement")
	List<Metrique> findByEquipementIdAndHorodatageGreaterThanEqualOrderByHorodatageDesc(
			UUID equipementId, LocalDateTime depuis, Pageable pageable);

	@EntityGraph(attributePaths = "equipement")
	List<Metrique> findByEquipementIdAndTypeMetriqueAndHorodatageGreaterThanEqualOrderByHorodatageDesc(
			UUID equipementId, TypeMetrique typeMetrique, LocalDateTime depuis, Pageable pageable);

	/**
	 * Dernier instant où la métrique est repassée <strong>sous</strong> le seuil.
	 *
	 * <p>C'est ce qui permet de savoir depuis combien de temps un dépassement se
	 * maintient sans relire toute la fenêtre : le dépassement est continu depuis
	 * cet instant. Renvoie {@code null} si la métrique n'est jamais descendue
	 * sous le seuil.
	 */
	@Query("""
			SELECT MAX(m.horodatage) FROM Metrique m
			WHERE m.equipement.id = :equipementId
			  AND m.typeMetrique = :typeMetrique
			  AND m.valeur < :seuil
			""")
	LocalDateTime dernierPassageSousSeuil(
			@Param("equipementId") UUID equipementId,
			@Param("typeMetrique") TypeMetrique typeMetrique,
			@Param("seuil") BigDecimal seuil);

	/** Première mesure connue, qui borne l'ancienneté d'un dépassement ininterrompu. */
	@Query("""
			SELECT MIN(m.horodatage) FROM Metrique m
			WHERE m.equipement.id = :equipementId
			  AND m.typeMetrique = :typeMetrique
			""")
	LocalDateTime premiereMesure(
			@Param("equipementId") UUID equipementId,
			@Param("typeMetrique") TypeMetrique typeMetrique);
}
