package com.bouba.backend_trans.rapport.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.rapport.dto.RapportGenerateRequest;
import com.bouba.backend_trans.rapport.entity.Rapport;
import com.bouba.backend_trans.rapport.entity.TypeRapport;
import com.bouba.backend_trans.rapport.repository.RapportRepository;

@Service
public class RapportService {

	private final RapportRepository rapportRepository;

	public RapportService(RapportRepository rapportRepository) {
		this.rapportRepository = rapportRepository;
	}

	@Transactional(readOnly = true)
	public List<Rapport> findAll() {
		return rapportRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Rapport findById(UUID id) {
		return rapportRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Rapport introuvable."));
	}

	/**
	 * Génère les métadonnées du rapport pour la période demandée (ou une période
	 * par défaut calculée à partir du type). La compilation effective du contenu
	 * (taux de disponibilité, alertes, équipements les plus sollicités) et
	 * l'export PDF restent à implémenter.
	 */
	@Transactional
	public Rapport generate(RapportGenerateRequest request) {
		Rapport rapport = new Rapport();
		rapport.setTypeRapport(request.getTypeRapport());
		rapport.setPeriodeDebut(
				request.getPeriodeDebut() != null ? request.getPeriodeDebut() : defautDebut(request.getTypeRapport()));
		rapport.setPeriodeFin(request.getPeriodeFin() != null ? request.getPeriodeFin() : LocalDateTime.now());
		return rapportRepository.save(rapport);
	}

	private LocalDateTime defautDebut(TypeRapport type) {
		LocalDate aujourdHui = LocalDate.now();
		return switch (type) {
			case JOURNALIER -> aujourdHui.minusDays(1).atStartOfDay();
			case HEBDOMADAIRE -> aujourdHui.minusDays(7).atStartOfDay();
			case MENSUEL -> aujourdHui.minusDays(30).atStartOfDay();
		};
	}
}
