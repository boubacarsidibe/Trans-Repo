package com.bouba.backend_trans.alerte.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bouba.backend_trans.alerte.entity.Alerte;
import com.bouba.backend_trans.alerte.entity.Severite;
import com.bouba.backend_trans.alerte.entity.StatutAlerte;
import com.bouba.backend_trans.alerte.entity.TypeAnomalie;

public interface AlerteRepository extends JpaRepository<Alerte, UUID> {

	@EntityGraph(attributePaths = {"equipement", "utilisateurPriseEnCharge"})
	List<Alerte> findAll();

	@EntityGraph(attributePaths = {"equipement", "utilisateurPriseEnCharge"})
	List<Alerte> findByStatutOrderByDateDeclenchementDesc(StatutAlerte statut);

	@EntityGraph(attributePaths = {"equipement", "utilisateurPriseEnCharge"})
	Optional<Alerte> findById(UUID id);

	Optional<Alerte> findFirstByEquipementIdAndTypeAnomalieAndStatutNot(
			UUID equipementId, TypeAnomalie typeAnomalie, StatutAlerte statut);

	/** Vrai si au moins une alerte existe pour cet équipement — bloque sa suppression définitive. */
	boolean existsByEquipementId(UUID equipementId);

	/** Alertes à rappeler tant que personne ne les a prises en charge (§11.4). */
	@EntityGraph(attributePaths = {"equipement", "utilisateurPriseEnCharge"})
	List<Alerte> findByStatutAndSeverite(StatutAlerte statut, Severite severite);

	// Variantes paginées et filtrables du §7.9. Les quatre combinaisons sont
	// explicites : un paramètre d'énumération nul dans une clause JPQL empêche
	// Hibernate d'inférer son type.
	@EntityGraph(attributePaths = {"equipement", "utilisateurPriseEnCharge"})
	List<Alerte> findAllBy(Pageable pageable);

	@EntityGraph(attributePaths = {"equipement", "utilisateurPriseEnCharge"})
	List<Alerte> findByStatut(StatutAlerte statut, Pageable pageable);

	@EntityGraph(attributePaths = {"equipement", "utilisateurPriseEnCharge"})
	List<Alerte> findBySeverite(Severite severite, Pageable pageable);

	@EntityGraph(attributePaths = {"equipement", "utilisateurPriseEnCharge"})
	List<Alerte> findByStatutAndSeverite(StatutAlerte statut, Severite severite, Pageable pageable);

	long countByDateDeclenchementBetween(LocalDateTime debut, LocalDateTime fin);

	long countByDateResolutionBetween(LocalDateTime debut, LocalDateTime fin);

	/** Équipements les plus sollicités sur la période, du plus alerté au moins (F8). */
	@Query("""
			SELECT a.equipement.nom, COUNT(a) FROM Alerte a
			WHERE a.dateDeclenchement BETWEEN :debut AND :fin
			GROUP BY a.equipement.nom
			ORDER BY COUNT(a) DESC
			""")
	List<Object[]> equipementsLesPlusSollicites(
			@Param("debut") LocalDateTime debut,
			@Param("fin") LocalDateTime fin);

	/**
	 * Alertes d'un type ouvertes à un moment quelconque de la période, y compris
	 * celles commencées avant ou toujours ouvertes après.
	 */
	@Query("""
			SELECT a FROM Alerte a
			WHERE a.typeAnomalie = :typeAnomalie
			  AND a.dateDeclenchement <= :fin
			  AND (a.dateResolution IS NULL OR a.dateResolution >= :debut)
			""")
	List<Alerte> chevauchantLaPeriode(
			@Param("typeAnomalie") TypeAnomalie typeAnomalie,
			@Param("debut") LocalDateTime debut,
			@Param("fin") LocalDateTime fin);
}
