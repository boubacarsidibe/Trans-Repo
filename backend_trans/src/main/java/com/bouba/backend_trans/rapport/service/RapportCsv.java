package com.bouba.backend_trans.rapport.service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.bouba.backend_trans.rapport.entity.Rapport;
import com.bouba.backend_trans.rapport.service.SyntheseRapport.EquipementSollicite;

/** Met en page le rapport d'exploitation exporté en CSV (F8). */
@Component
public class RapportCsv {

	private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
	private static final byte[] BOM_UTF8 = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	public byte[] produire(Rapport rapport, SyntheseRapport synthese) {
		ByteArrayOutputStream sortie = new ByteArrayOutputStream();
		// BOM UTF-8 : sans lui, Excel ouvre les accents en charabia.
		sortie.writeBytes(BOM_UTF8);

		try (PrintWriter ecrivain = new PrintWriter(sortie, false, StandardCharsets.UTF_8)) {
			ecrivain.println("Rapport;" + rapport.getTypeRapport().name().toLowerCase());
			ecrivain.println("Periode;" + rapport.getPeriodeDebut().format(DATE_HEURE) + " au "
					+ rapport.getPeriodeFin().format(DATE_HEURE));
			ecrivain.println("Genere le;" + rapport.getDateGeneration().format(DATE_HEURE));
			ecrivain.println();

			ecrivain.println("Indicateur;Valeur");
			ecrivain.println("Taux de disponibilite du parc (%);" + synthese.tauxDisponibilite());
			ecrivain.println("Equipements supervises;" + synthese.equipementsSupervises());
			ecrivain.println("Alertes declenchees;" + synthese.alertesDeclenchees());
			ecrivain.println("Alertes resolues;" + synthese.alertesResolues());
			ecrivain.println();

			ecrivain.println("Equipement;Alertes");
			if (synthese.equipementsLesPlusSollicites().isEmpty()) {
				ecrivain.println("(aucune alerte sur la periode);0");
			} else {
				for (EquipementSollicite equipement : synthese.equipementsLesPlusSollicites()) {
					ecrivain.println(echapper(equipement.nom()) + ";" + equipement.alertes());
				}
			}
			ecrivain.flush();
		}

		return sortie.toByteArray();
	}

	private String echapper(String valeur) {
		if (valeur == null) {
			return "";
		}
		if (valeur.contains(";") || valeur.contains("\"") || valeur.contains("\n")) {
			return "\"" + valeur.replace("\"", "\"\"") + "\"";
		}
		return valeur;
	}
}
