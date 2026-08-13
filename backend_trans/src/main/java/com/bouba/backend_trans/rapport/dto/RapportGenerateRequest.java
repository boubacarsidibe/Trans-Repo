package com.bouba.backend_trans.rapport.dto;

import java.time.LocalDateTime;

import com.bouba.backend_trans.rapport.entity.TypeRapport;

import jakarta.validation.constraints.NotNull;

public class RapportGenerateRequest {

	@NotNull
	private TypeRapport typeRapport;

	private LocalDateTime periodeDebut;

	private LocalDateTime periodeFin;

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
}
