package com.bouba.backend_trans.rapport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bouba.backend_trans.rapport.dto.RapportGenerateRequest;
import com.bouba.backend_trans.rapport.entity.Rapport;
import com.bouba.backend_trans.rapport.entity.TypeRapport;
import com.bouba.backend_trans.rapport.repository.RapportRepository;

/**
 * Génération à la demande (F8), consultation et téléchargement des rapports.
 */
class RapportServiceTest {

	private final RapportRepository rapportRepository = mock(RapportRepository.class);
	private final RapportCalculateur calculateur = mock(RapportCalculateur.class);
	private final RapportPdf rapportPdf = mock(RapportPdf.class);

	@TempDir
	private Path repertoire;

	private RapportService rapportService;

	@BeforeEach
	void initService() {
		rapportService = new RapportService(rapportRepository, calculateur, rapportPdf, repertoire.toString());
	}

	@Test
	void trouve_un_rapport_existant() {
		Rapport rapport = rapport();
		when(rapportRepository.findById(rapport.getId())).thenReturn(Optional.of(rapport));

		assertThat(rapportService.findById(rapport.getId())).isSameAs(rapport);
	}

	@Test
	void leve_une_exception_si_le_rapport_est_introuvable() {
		UUID id = UUID.randomUUID();
		when(rapportRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> rapportService.findById(id)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void genere_un_rapport_avec_la_periode_fournie_et_ecrit_le_pdf() {
		LocalDateTime debut = LocalDateTime.of(2026, 8, 1, 0, 0);
		LocalDateTime fin = LocalDateTime.of(2026, 8, 2, 0, 0);
		RapportGenerateRequest demande = new RapportGenerateRequest();
		demande.setTypeRapport(TypeRapport.JOURNALIER);
		demande.setPeriodeDebut(debut);
		demande.setPeriodeFin(fin);

		SyntheseRapport synthese = new SyntheseRapport(BigDecimal.valueOf(99), 1, 1, 5, List.of());
		when(calculateur.calculer(debut, fin)).thenReturn(synthese);
		when(rapportPdf.produire(any(), any())).thenReturn(new byte[] { 1, 2, 3 });
		when(rapportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Rapport rapport = rapportService.generate(demande);

		assertThat(rapport.getTypeRapport()).isEqualTo(TypeRapport.JOURNALIER);
		assertThat(rapport.getPeriodeDebut()).isEqualTo(debut);
		assertThat(rapport.getPeriodeFin()).isEqualTo(fin);
		assertThat(rapport.getCheminFichierPdf()).isNotNull();
		assertThat(Path.of(rapport.getCheminFichierPdf())).exists();
		verify(rapportRepository, org.mockito.Mockito.times(2)).save(any());
	}

	@Test
	void genere_un_rapport_avec_la_periode_par_defaut_de_la_veille_quand_absente() {
		RapportGenerateRequest demande = new RapportGenerateRequest();
		demande.setTypeRapport(TypeRapport.JOURNALIER);

		when(calculateur.calculer(any(), any()))
				.thenReturn(new SyntheseRapport(BigDecimal.valueOf(99), 0, 0, 0, List.of()));
		when(rapportPdf.produire(any(), any())).thenReturn(new byte[0]);
		when(rapportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Rapport rapport = rapportService.generate(demande);

		assertThat(rapport.getPeriodeDebut()).isEqualTo(java.time.LocalDate.now().minusDays(1).atStartOfDay());
	}

	@Test
	void la_synthese_recalcule_les_indicateurs_sur_la_periode_du_rapport() {
		Rapport rapport = rapport();
		SyntheseRapport synthese = new SyntheseRapport(BigDecimal.valueOf(100), 0, 0, 3, List.of());
		when(calculateur.calculer(rapport.getPeriodeDebut(), rapport.getPeriodeFin())).thenReturn(synthese);

		assertThat(rapportService.synthese(rapport)).isSameAs(synthese);
	}

	@Test
	void lit_le_fichier_pdf_d_un_rapport_deja_genere() throws IOException {
		Path fichier = repertoire.resolve("rapport-test.pdf");
		Files.write(fichier, new byte[] { 9, 8, 7 });
		Rapport rapport = rapport();
		rapport.setCheminFichierPdf(fichier.toString());
		when(rapportRepository.findById(rapport.getId())).thenReturn(Optional.of(rapport));

		byte[] contenu = rapportService.fichier(rapport.getId());

		assertThat(contenu).containsExactly(9, 8, 7);
	}

	@Test
	void refuse_de_lire_le_fichier_d_un_rapport_sans_pdf_genere() {
		Rapport rapport = rapport();
		rapport.setCheminFichierPdf(null);
		when(rapportRepository.findById(rapport.getId())).thenReturn(Optional.of(rapport));

		assertThatThrownBy(() -> rapportService.fichier(rapport.getId())).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void signale_un_fichier_pdf_manquant_sur_le_disque() {
		Rapport rapport = rapport();
		rapport.setCheminFichierPdf(repertoire.resolve("introuvable.pdf").toString());
		when(rapportRepository.findById(rapport.getId())).thenReturn(Optional.of(rapport));

		assertThatThrownBy(() -> rapportService.fichier(rapport.getId())).isInstanceOf(UncheckedIOException.class);
	}

	private Rapport rapport() {
		Rapport rapport = new Rapport();
		rapport.setId(UUID.randomUUID());
		rapport.setTypeRapport(TypeRapport.JOURNALIER);
		rapport.setPeriodeDebut(LocalDateTime.of(2026, 8, 1, 0, 0));
		rapport.setPeriodeFin(LocalDateTime.of(2026, 8, 2, 0, 0));
		return rapport;
	}
}
