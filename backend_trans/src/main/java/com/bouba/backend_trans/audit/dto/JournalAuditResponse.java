package com.bouba.backend_trans.audit.dto;

import java.time.LocalDateTime;

import com.bouba.backend_trans.audit.entity.JournalAudit;

public class JournalAuditResponse {

	private Long id;
	private String utilisateurEmail;
	private String action;
	private LocalDateTime horodatage;
	private String adresseIpSource;

	public static JournalAuditResponse fromEntity(JournalAudit journalAudit) {
		JournalAuditResponse response = new JournalAuditResponse();
		response.id = journalAudit.getId();
		response.utilisateurEmail = journalAudit.getUtilisateur().getEmail();
		response.action = journalAudit.getAction();
		response.horodatage = journalAudit.getHorodatage();
		response.adresseIpSource = journalAudit.getAdresseIpSource();
		return response;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUtilisateurEmail() {
		return utilisateurEmail;
	}

	public void setUtilisateurEmail(String utilisateurEmail) {
		this.utilisateurEmail = utilisateurEmail;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public LocalDateTime getHorodatage() {
		return horodatage;
	}

	public void setHorodatage(LocalDateTime horodatage) {
		this.horodatage = horodatage;
	}

	public String getAdresseIpSource() {
		return adresseIpSource;
	}

	public void setAdresseIpSource(String adresseIpSource) {
		this.adresseIpSource = adresseIpSource;
	}
}
