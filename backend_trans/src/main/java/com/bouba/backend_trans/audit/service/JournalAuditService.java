package com.bouba.backend_trans.audit.service;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.audit.entity.JournalAudit;
import com.bouba.backend_trans.audit.repository.JournalAuditRepository;
import com.bouba.backend_trans.auth.entity.AppUser;

@Service
public class JournalAuditService {

	private final JournalAuditRepository journalAuditRepository;

	public JournalAuditService(JournalAuditRepository journalAuditRepository) {
		this.journalAuditRepository = journalAuditRepository;
	}

	@Transactional
	public void enregistrer(AppUser utilisateur, String action, String adresseIpSource) {
		JournalAudit entree = new JournalAudit();
		entree.setUtilisateur(utilisateur);
		entree.setAction(action);
		entree.setAdresseIpSource(adresseIpSource);
		journalAuditRepository.save(entree);
	}

	@Transactional(readOnly = true)
	public List<JournalAudit> findAll() {
		return journalAuditRepository.findAllByOrderByHorodatageDesc();
	}

	@Transactional(readOnly = true)
	public List<JournalAudit> findAll(Pageable pageable) {
		return journalAuditRepository.findAllByOrderByHorodatageDesc(pageable);
	}
}
