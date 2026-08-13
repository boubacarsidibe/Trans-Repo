package com.bouba.backend_trans.seuil.service;

import java.math.BigDecimal;

/**
 * Valeurs de seuil résolues pour un couple équipement/métrique.
 *
 * <p>Objet immuable volontairement détaché de l'entité : il est mis en cache et
 * lu à chaque métrique ingérée, hors de toute session JPA.
 */
public record Seuil(BigDecimal avertissement, BigDecimal critique, int dureeSecondes) {
}
