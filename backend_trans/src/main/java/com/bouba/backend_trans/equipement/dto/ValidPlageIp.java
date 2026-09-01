package com.bouba.backend_trans.equipement.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Valide une {@link ScanRequest} dans son ensemble : format IPv4 de
 * {@code ipDebut}/{@code ipFin}, {@code ipFin} postérieure ou égale à
 * {@code ipDebut}, et taille de la plage sous {@link ScanRequest#TAILLE_MAX_PLAGE}.
 */
@Documented
@Constraint(validatedBy = PlageIpValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPlageIp {

	String message() default "Plage IP invalide.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
