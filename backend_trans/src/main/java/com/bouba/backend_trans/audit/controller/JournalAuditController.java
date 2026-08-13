package com.bouba.backend_trans.audit.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.audit.dto.JournalAuditResponse;
import com.bouba.backend_trans.audit.service.JournalAuditService;

@RestController
@RequestMapping("/api/v1/audit-log")
@PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATEUR')")
public class JournalAuditController {

	private static final int TAILLE_MAXIMALE = 1000;

	private final JournalAuditService journalAuditService;

	public JournalAuditController(JournalAuditService journalAuditService) {
		this.journalAuditService = journalAuditService;
	}

	/** Consultation paginée : le journal d'audit ne cesse de croître (§7.9). */
	@GetMapping
	public List<JournalAuditResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "200") int taille
	) {
		Pageable pagination = PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), TAILLE_MAXIMALE));

		return journalAuditService.findAll(pagination).stream()
				.map(JournalAuditResponse::fromEntity)
				.collect(Collectors.toList());
	}
}
