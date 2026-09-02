package com.bouba.backend_trans.audit.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bouba.backend_trans.audit.entity.JournalAudit;

public interface JournalAuditRepository extends JpaRepository<JournalAudit, Long> {

	@EntityGraph(attributePaths = "utilisateur")
	List<JournalAudit> findAllByOrderByHorodatageDesc();

	/** Le journal ne cesse de croître : sa consultation est paginée (§7.9). */
	@EntityGraph(attributePaths = "utilisateur")
	List<JournalAudit> findAllByOrderByHorodatageDesc(Pageable pageable);

	/** Vrai si au moins une entrée du journal référence cet utilisateur — bloque sa suppression définitive. */
	boolean existsByUtilisateurId(Long utilisateurId);
}
