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

class RapportCsvTest {

	private final RapportCsv rapportCsv = new RapportCsv();

	@Test
	void produit_un_csv_avec_le_bom_utf8_et_les_indicateurs_de_la_regle_F8() {
		byte[] csv = rapportCsv.produire(rapport(), synthese());

		assertThat(csv).isNotEmpty();
		assertThat(csv[0]).isEqualTo((byte) 0xEF);
		assertThat(csv[1]).isEqualTo((byte) 0xBB);
		assertThat(csv[2]).isEqualTo((byte) 0xBF);

		String contenu = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
		assertThat(contenu)
				.contains("Taux de disponibilite du parc (%);99.42")
				.contains("Alertes declenchees;7")
				.contains("Alertes resolues;5")
				.contains("sw-core-02;4")
				.contains("srv-moodle;2");
	}

	@Test
	void tient_debout_sans_aucune_alerte_sur_la_periode() {
		SyntheseRapport vide = new SyntheseRapport(new BigDecimal("100.00"), 0, 0, 12, List.of());

		byte[] csv = rapportCsv.produire(rapport(), vide);

		String contenu = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
		assertThat(contenu).contains("(aucune alerte sur la periode);0");
	}

	@Test
	void echappe_les_noms_d_equipement_contenant_le_separateur() {
		SyntheseRapport avecPointVirgule = new SyntheseRapport(
				new BigDecimal("100.00"), 1, 0, 1,
				List.of(new EquipementSollicite("switch; salle A", 1)));

		byte[] csv = rapportCsv.produire(rapport(), avecPointVirgule);

		String contenu = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
		assertThat(contenu).contains("\"switch; salle A\";1");
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
