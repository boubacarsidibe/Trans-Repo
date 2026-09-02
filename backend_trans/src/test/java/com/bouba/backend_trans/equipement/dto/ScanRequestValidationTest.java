package com.bouba.backend_trans.equipement.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Validation de {@link ScanRequest} (issue #152) : rejet d'une plage trop
 * grande, rejet d'un format d'IP invalide. Bean Validation pure, sans
 * contexte Spring ni appel réseau.
 */
class ScanRequestValidationTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void creerValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void fermerValidator() {
		factory.close();
	}

	@Test
	void accepte_une_plage_valide() {
		ScanRequest request = requete("192.168.1.1", "192.168.1.10", "public", 161);

		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	void accepte_une_plage_de_taille_maximale_254_adresses() {
		ScanRequest request = requete("10.0.0.1", "10.0.0.254", "public", 161);

		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	void rejette_une_plage_depassant_la_taille_maximale_autorisee() {
		ScanRequest request = requete("10.0.0.0", "10.0.1.0", "public", 161);

		Set<ConstraintViolation<ScanRequest>> violations = validator.validate(request);

		assertThat(violations).isNotEmpty();
		assertThat(violations)
				.anyMatch(v -> v.getMessage().contains("taille maximale"));
	}

	@Test
	void rejette_un_format_d_adresse_de_debut_invalide() {
		ScanRequest request = requete("adresse-invalide", "192.168.1.10", "public", 161);

		Set<ConstraintViolation<ScanRequest>> violations = validator.validate(request);

		assertThat(violations).isNotEmpty();
		assertThat(violations).anyMatch(v -> v.getMessage().contains("début invalide"));
	}

	@Test
	void rejette_un_format_d_adresse_de_fin_invalide() {
		ScanRequest request = requete("192.168.1.1", "192.168.1.999", "public", 161);

		Set<ConstraintViolation<ScanRequest>> violations = validator.validate(request);

		assertThat(violations).isNotEmpty();
		assertThat(violations).anyMatch(v -> v.getMessage().contains("fin invalide"));
	}

	@Test
	void rejette_une_plage_dont_la_fin_precede_le_debut() {
		ScanRequest request = requete("192.168.1.20", "192.168.1.10", "public", 161);

		Set<ConstraintViolation<ScanRequest>> violations = validator.validate(request);

		assertThat(violations).isNotEmpty();
		assertThat(violations).anyMatch(v -> v.getMessage().contains("postérieure ou égale"));
	}

	@Test
	void rejette_une_communaute_manquante() {
		ScanRequest request = requete("192.168.1.1", "192.168.1.10", "", 161);

		assertThat(validator.validate(request)).isNotEmpty();
	}

	@Test
	void rejette_un_port_hors_bornes() {
		ScanRequest request = requete("192.168.1.1", "192.168.1.10", "public", 70000);

		assertThat(validator.validate(request)).isNotEmpty();
	}

	private ScanRequest requete(String ipDebut, String ipFin, String communaute, int port) {
		ScanRequest request = new ScanRequest();
		request.setIpDebut(ipDebut);
		request.setIpFin(ipFin);
		request.setCommunaute(communaute);
		request.setPort(port);
		return request;
	}
}
