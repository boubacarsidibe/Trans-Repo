package com.bouba.backend_trans.metrique.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.bouba.backend_trans.metrique.entity.Metrique;
import com.bouba.backend_trans.metrique.entity.TypeMetrique;

public class MetriqueResponse {

	private Long id;
	private TypeMetrique typeMetrique;
	private BigDecimal valeur;
	private String unite;
	private LocalDateTime horodatage;

	public static MetriqueResponse fromEntity(Metrique metrique) {
		MetriqueResponse response = new MetriqueResponse();
		response.id = metrique.getId();
		response.typeMetrique = metrique.getTypeMetrique();
		response.valeur = metrique.getValeur();
		response.unite = metrique.getUnite();
		response.horodatage = metrique.getHorodatage();
		return response;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public TypeMetrique getTypeMetrique() {
		return typeMetrique;
	}

	public void setTypeMetrique(TypeMetrique typeMetrique) {
		this.typeMetrique = typeMetrique;
	}

	public BigDecimal getValeur() {
		return valeur;
	}

	public void setValeur(BigDecimal valeur) {
		this.valeur = valeur;
	}

	public String getUnite() {
		return unite;
	}

	public void setUnite(String unite) {
		this.unite = unite;
	}

	public LocalDateTime getHorodatage() {
		return horodatage;
	}

	public void setHorodatage(LocalDateTime horodatage) {
		this.horodatage = horodatage;
	}
}
