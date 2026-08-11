package com.bouba.backend_trans.audit.entity;

import java.time.LocalDateTime;

import com.bouba.backend_trans.auth.entity.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "journal_audit")
public class JournalAudit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "utilisateur_id", nullable = false)
	private AppUser utilisateur;

	@Column(nullable = false, length = 255)
	private String action;

	@Column(nullable = false)
	private LocalDateTime horodatage;

	@Column(name = "adresse_ip_source", length = 45)
	private String adresseIpSource;

	@PrePersist
	void onCreate() {
		if (horodatage == null) {
			horodatage = LocalDateTime.now();
		}
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public AppUser getUtilisateur() {
		return utilisateur;
	}

	public void setUtilisateur(AppUser utilisateur) {
		this.utilisateur = utilisateur;
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
