package com.bouba.backend_trans.rapport.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bouba.backend_trans.rapport.entity.Rapport;
import com.bouba.backend_trans.rapport.entity.TypeRapport;
import com.bouba.backend_trans.rapport.service.SyntheseRapport;
import com.fasterxml.jackson.annotation.JsonInclude;

public class RapportResponse {

	private UUID id;
	private TypeRapport typeRapport;
	private LocalDateTime periodeDebut;
	private LocalDateTime periodeFin;
	private LocalDateTime dateGeneration;
	private boolean fichierDisponible;

	/**
	 * Indicateurs de la période, renseignés à la génération pour l'aperçu avant
	 * export (§10.5). Absents de la liste, qui reste une vue de métadonnées.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private SyntheseRapport synthese;

	public static RapportResponse fromEntity(Rapport rapport, SyntheseRapport synthese) {
		RapportResponse response = fromEntity(rapport);
		response.synthese = synthese;
		return response;
	}

	public static RapportResponse fromEntity(Rapport rapport) {
		RapportResponse response = new RapportResponse();
		response.id = rapport.getId();
		response.typeRapport = rapport.getTypeRapport();
		response.periodeDebut = rapport.getPeriodeDebut();
		response.periodeFin = rapport.getPeriodeFin();
		response.dateGeneration = rapport.getDateGeneration();
		response.fichierDisponible = rapport.getCheminFichierPdf() != null;
		return response;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public TypeRapport getTypeRapport() {
		return typeRapport;
	}

	public void setTypeRapport(TypeRapport typeRapport) {
		this.typeRapport = typeRapport;
	}

	public LocalDateTime getPeriodeDebut() {
		return periodeDebut;
	}

	public void setPeriodeDebut(LocalDateTime periodeDebut) {
		this.periodeDebut = periodeDebut;
	}

	public LocalDateTime getPeriodeFin() {
		return periodeFin;
	}

	public void setPeriodeFin(LocalDateTime periodeFin) {
		this.periodeFin = periodeFin;
	}

	public LocalDateTime getDateGeneration() {
		return dateGeneration;
	}

	public void setDateGeneration(LocalDateTime dateGeneration) {
		this.dateGeneration = dateGeneration;
	}

	public SyntheseRapport getSynthese() {
		return synthese;
	}

	public void setSynthese(SyntheseRapport synthese) {
		this.synthese = synthese;
	}

	public boolean isFichierDisponible() {
		return fichierDisponible;
	}

	public void setFichierDisponible(boolean fichierDisponible) {
		this.fichierDisponible = fichierDisponible;
	}
}
