package com.bouba.backend_trans.rapport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bouba.backend_trans.rapport.entity.Rapport;
import com.bouba.backend_trans.rapport.entity.TypeRapport;
import com.bouba.backend_trans.rapport.service.SyntheseRapport.EquipementSollicite;

class RapportPdfTest {

	private final RapportPdf rapportPdf = new RapportPdf();

	@Test
	void produit_un_pdf_valide_avec_les_indicateurs_de_la_regle_F8() {
		byte[] pdf = rapportPdf.produire(rapport(), synthese());

		assertThat(pdf).isNotEmpty();
		// Signature d'en-tête d'un fichier PDF.
		assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
	}

	@Test
	void tient_debout_sans_aucune_alerte_sur_la_periode() {
		SyntheseRapport vide = new SyntheseRapport(
				new BigDecimal("100.00"), 0, 0, 12, List.of());

		byte[] pdf = rapportPdf.produire(rapport(), vide);

		assertThat(pdf).isNotEmpty();
	}

	private Rapport rapport() {
		Rapport rapport = new Rapport();
		rapport.setId(UUID.randomUUID());
		rapport.setTypeRapport(TypeRapport.JOURNALIER);
		rapport.setPeriodeDebut(LocalDateTime.of(2026, 8, 11, 0, 0));
		rapport.setPeriodeFin(LocalDateTime.of(2026, 8, 12, 0, 0));
		rapport.setDateGeneration(LocalDateTime.of(2026, 8, 12, 1, 15));
		return rapport;
	}

	private SyntheseRapport synthese() {
		return new SyntheseRapport(
				new BigDecimal("99.42"),
				7,
				5,
				12,
				List.of(
						new EquipementSollicite("sw-core-02", 4),
						new EquipementSollicite("srv-moodle", 2)));
	}
}
