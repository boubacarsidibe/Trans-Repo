package com.bouba.backend_trans.equipement.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PlageIpUtilsTest {

	@Test
	void reconnait_une_adresse_ipv4_bien_formee() {
		assertThat(PlageIpUtils.estFormatIpv4Valide("192.168.1.10")).isTrue();
		assertThat(PlageIpUtils.estFormatIpv4Valide("0.0.0.0")).isTrue();
		assertThat(PlageIpUtils.estFormatIpv4Valide("255.255.255.255")).isTrue();
	}

	@Test
	void rejette_un_format_ipv4_invalide() {
		assertThat(PlageIpUtils.estFormatIpv4Valide(null)).isFalse();
		assertThat(PlageIpUtils.estFormatIpv4Valide("")).isFalse();
		assertThat(PlageIpUtils.estFormatIpv4Valide("192.168.1")).isFalse();
		assertThat(PlageIpUtils.estFormatIpv4Valide("192.168.1.256")).isFalse();
		assertThat(PlageIpUtils.estFormatIpv4Valide("pas-une-ip")).isFalse();
		assertThat(PlageIpUtils.estFormatIpv4Valide("192.168.1.1.1")).isFalse();
	}

	@Test
	void convertit_une_adresse_en_entier_non_signe_et_inversement() {
		long valeur = PlageIpUtils.versEntierNonSigne("192.168.1.10");

		assertThat(PlageIpUtils.depuisEntierNonSigne(valeur)).isEqualTo("192.168.1.10");
	}

	@Test
	void enumere_toutes_les_adresses_de_la_plage_bornes_incluses() {
		List<String> adresses = PlageIpUtils.enumererPlage("192.168.1.1", "192.168.1.5");

		assertThat(adresses).containsExactly(
				"192.168.1.1", "192.168.1.2", "192.168.1.3", "192.168.1.4", "192.168.1.5");
	}

	@Test
	void enumere_une_plage_reduite_a_une_seule_adresse() {
		assertThat(PlageIpUtils.enumererPlage("10.0.0.5", "10.0.0.5")).containsExactly("10.0.0.5");
	}
}
