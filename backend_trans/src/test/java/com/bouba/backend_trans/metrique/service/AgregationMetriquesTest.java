package com.bouba.backend_trans.metrique.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Les replis du §6.10 sont écrits en SQL natif : seule une exécution réelle
 * prouve que les instructions sont valides et que les contraintes d'unicité
 * nécessaires au {@code ON CONFLICT} existent bien en base.
 */
@SpringBootTest
class AgregationMetriquesTest {

	@Autowired
	private AgregationMetriques agregationMetriques;

	@Test
	void les_instructions_de_repli_s_executent_sur_le_schema_reel() {
		assertThatCode(() -> agregationMetriques.appliquerLaRetention()).doesNotThrowAnyException();
	}
}
