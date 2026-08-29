package com.bouba.backend_trans.audit.service;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

	// REQUIRES_NEW : l'ecriture d'audit est independante de la transaction de
	// l'action metier qui la declenche (issue #44, AuditAspect) - elle commite
	// meme si l'appelant tourne dans une transaction plus large qui echoue
	// ensuite pour une autre raison.
	@Transactional(propagation = Propagation.REQUIRES_NEW)
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
