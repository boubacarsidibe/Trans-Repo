package com.bouba.backend_trans.rapport.service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.bouba.backend_trans.rapport.entity.Rapport;
import com.bouba.backend_trans.rapport.service.SyntheseRapport.EquipementSollicite;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/** Met en page le rapport d'exploitation exporté en PDF (F8). */
@Component
public class RapportPdf {

	private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private static final Font TITRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
	private static final Font SOUS_TITRE = FontFactory.getFont(FontFactory.HELVETICA, 10);
	private static final Font SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
	private static final Font CORPS = FontFactory.getFont(FontFactory.HELVETICA, 10);
	private static final Font CHIFFRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

	public byte[] produire(Rapport rapport, SyntheseRapport synthese) {
		ByteArrayOutputStream sortie = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4, 48, 48, 48, 48);
		PdfWriter.getInstance(document, sortie);
		document.open();

		document.add(titre("Rapport d'exploitation — Supervision EPT"));
		document.add(ligne("École Polytechnique de Thiès — Centre des Ressources Informatiques", SOUS_TITRE));
		document.add(ligne("Rapport %s du %s au %s".formatted(
				rapport.getTypeRapport().name().toLowerCase(),
				rapport.getPeriodeDebut().format(DATE_HEURE),
				rapport.getPeriodeFin().format(DATE_HEURE)), SOUS_TITRE));
		document.add(ligne("Généré le " + rapport.getDateGeneration().format(DATE_HEURE), SOUS_TITRE));

		document.add(espace());
		document.add(ligne("Indicateurs de la période", SECTION));

		PdfPTable indicateurs = new PdfPTable(2);
		indicateurs.setWidthPercentage(100);
		ajouterLigne(indicateurs, "Taux de disponibilité du parc", synthese.tauxDisponibilite() + " %");
		ajouterLigne(indicateurs, "Équipements supervisés", String.valueOf(synthese.equipementsSupervises()));
		ajouterLigne(indicateurs, "Alertes déclenchées", String.valueOf(synthese.alertesDeclenchees()));
		ajouterLigne(indicateurs, "Alertes résolues", String.valueOf(synthese.alertesResolues()));
		document.add(indicateurs);

		document.add(espace());
		document.add(ligne("Équipements les plus sollicités", SECTION));

		if (synthese.equipementsLesPlusSollicites().isEmpty()) {
			document.add(ligne("Aucune alerte sur la période.", CORPS));
		} else {
			PdfPTable classement = new PdfPTable(2);
			classement.setWidthPercentage(100);
			ajouterEntete(classement, "Équipement", "Alertes");
			for (EquipementSollicite equipement : synthese.equipementsLesPlusSollicites()) {
				ajouterLigne(classement, equipement.nom(), String.valueOf(equipement.alertes()));
			}
			document.add(classement);
		}

		document.close();
		return sortie.toByteArray();
	}

	private Paragraph titre(String texte) {
		Paragraph paragraphe = new Paragraph(texte, TITRE);
		paragraphe.setSpacingAfter(6);
		return paragraphe;
	}

	private Paragraph ligne(String texte, Font police) {
		Paragraph paragraphe = new Paragraph(texte, police);
		paragraphe.setSpacingAfter(4);
		return paragraphe;
	}

	private Paragraph espace() {
		Paragraph paragraphe = new Paragraph(" ");
		paragraphe.setSpacingAfter(8);
		return paragraphe;
	}

	private void ajouterEntete(PdfPTable table, String gauche, String droite) {
		table.addCell(cellule(gauche, CHIFFRE, Element.ALIGN_LEFT));
		table.addCell(cellule(droite, CHIFFRE, Element.ALIGN_RIGHT));
	}

	private void ajouterLigne(PdfPTable table, String libelle, String valeur) {
		table.addCell(cellule(libelle, CORPS, Element.ALIGN_LEFT));
		table.addCell(cellule(valeur, CHIFFRE, Element.ALIGN_RIGHT));
	}

	private PdfPCell cellule(String texte, Font police, int alignement) {
		PdfPCell cellule = new PdfPCell(new Paragraph(texte, police));
		cellule.setHorizontalAlignment(alignement);
		cellule.setPadding(6);
		return cellule;
	}
}
