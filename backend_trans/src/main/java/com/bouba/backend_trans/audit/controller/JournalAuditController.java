package com.bouba.backend_trans.audit.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bouba.backend_trans.audit.dto.JournalAuditResponse;
import com.bouba.backend_trans.audit.service.JournalAuditService;

@RestController
@RequestMapping("/api/v1/audit-log")
@PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATEUR')")
public class JournalAuditController {

	private final JournalAuditService journalAuditService;

	public JournalAuditController(JournalAuditService journalAuditService) {
		this.journalAuditService = journalAuditService;
	}

	@GetMapping
	public List<JournalAuditResponse> list() {
		return journalAuditService.findAll().stream()
				.map(JournalAuditResponse::fromEntity)
				.collect(Collectors.toList());
	}
}
