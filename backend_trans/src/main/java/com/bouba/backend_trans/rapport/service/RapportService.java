package com.bouba.backend_trans.rapport.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bouba.backend_trans.rapport.dto.RapportGenerateRequest;
import com.bouba.backend_trans.rapport.entity.Rapport;
import com.bouba.backend_trans.rapport.entity.TypeRapport;
import com.bouba.backend_trans.rapport.repository.RapportRepository;

@Service
public class RapportService {

	private static final Logger log = LoggerFactory.getLogger(RapportService.class);

	private final RapportRepository rapportRepository;
	private final RapportCalculateur calculateur;
	private final RapportPdf rapportPdf;
	private final RapportCsv rapportCsv;
	private final Path repertoire;

	public RapportService(
			RapportRepository rapportRepository,
			RapportCalculateur calculateur,
			RapportPdf rapportPdf,
			RapportCsv rapportCsv,
			@Value("${app.rapports.repertoire}") String repertoire
	) {
		this.rapportRepository = rapportRepository;
		this.calculateur = calculateur;
		this.rapportPdf = rapportPdf;
		this.rapportCsv = rapportCsv;
		this.repertoire = Path.of(repertoire);
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
	 * Compile les indicateurs de la période, écrit le PDF et enregistre le
	 * rapport (F8).
	 */
	@Transactional
	public Rapport generate(RapportGenerateRequest request) {
		Rapport rapport = new Rapport();
		rapport.setTypeRapport(request.getTypeRapport());
		rapport.setPeriodeDebut(
				request.getPeriodeDebut() != null ? request.getPeriodeDebut() : defautDebut(request.getTypeRapport()));
		rapport.setPeriodeFin(request.getPeriodeFin() != null ? request.getPeriodeFin() : LocalDateTime.now());
		rapport.setDateGeneration(LocalDateTime.now());

		SyntheseRapport synthese = calculateur.calculer(rapport.getPeriodeDebut(), rapport.getPeriodeFin());
		rapport = rapportRepository.save(rapport);
		rapport.setCheminFichierPdf(ecrirePdf(rapport, synthese));

		return rapportRepository.save(rapport);
	}

	/** Recalcule les indicateurs d'un rapport déjà enregistré, pour l'aperçu (§10.5). */
	@Transactional(readOnly = true)
	public SyntheseRapport synthese(Rapport rapport) {
		return calculateur.calculer(rapport.getPeriodeDebut(), rapport.getPeriodeFin());
	}

	@Transactional(readOnly = true)
	public byte[] fichier(UUID id) {
		Rapport rapport = findById(id);
		if (rapport.getCheminFichierPdf() == null) {
			throw new IllegalStateException("Aucun fichier n'a été produit pour ce rapport.");
		}

		try {
			return Files.readAllBytes(Path.of(rapport.getCheminFichierPdf()));
		} catch (IOException ex) {
			throw new UncheckedIOException("Le fichier du rapport est introuvable sur le serveur.", ex);
		}
	}

	/**
	 * Recalcule les indicateurs et produit le CSV à la volée : contrairement au
	 * PDF, il n'est pas persisté sur disque (peu coûteux à régénérer).
	 */
	@Transactional(readOnly = true)
	public byte[] csv(UUID id) {
		Rapport rapport = findById(id);
		SyntheseRapport synthese = calculateur.calculer(rapport.getPeriodeDebut(), rapport.getPeriodeFin());
		return rapportCsv.produire(rapport, synthese);
	}

	private String ecrirePdf(Rapport rapport, SyntheseRapport synthese) {
		try {
			Files.createDirectories(repertoire);
			Path fichier = repertoire.resolve("rapport-%s-%s.pdf".formatted(
					rapport.getTypeRapport().name().toLowerCase(), rapport.getId()));
			Files.write(fichier, rapportPdf.produire(rapport, synthese));
			return fichier.toString();
		} catch (IOException ex) {
			// Le rapport reste consultable même si l'écriture du PDF échoue :
			// perdre l'export ne doit pas perdre les indicateurs.
			log.error("Écriture du PDF du rapport {} impossible : {}", rapport.getId(), ex.getMessage());
			return null;
		}
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
