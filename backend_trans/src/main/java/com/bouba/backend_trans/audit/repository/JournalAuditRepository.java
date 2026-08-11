package com.bouba.backend_trans.audit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bouba.backend_trans.audit.entity.JournalAudit;

public interface JournalAuditRepository extends JpaRepository<JournalAudit, Long> {

	@EntityGraph(attributePaths = "utilisateur")
	List<JournalAudit> findAllByOrderByHorodatageDesc();
}
