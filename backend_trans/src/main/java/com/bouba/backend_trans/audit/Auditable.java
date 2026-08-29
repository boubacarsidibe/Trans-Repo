package com.bouba.backend_trans.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Pose une entrée dans le journal d'audit après le succès d'une méthode de
 * service, sans appel manuel dispersé (issue #44). {@link AuditAspect}
 * résout l'utilisateur authentifié courant et l'adresse IP de la requête ;
 * la méthode annotée n'a besoin de rien connaître de l'audit.
 *
 * <p>Réservée aux actions déclenchées par un utilisateur via l'API (donc
 * avec un contexte de sécurité/requête HTTP) : une méthode interne ou
 * déclenchée par le système (planification, amorçage) que rien n'authentifie
 * n'a rien à auditer, et {@link AuditAspect} l'ignore silencieusement dans
 * ce cas plutôt que d'échouer.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {

	/** Libellé enregistré tel quel dans {@code journal_audit.action} (ex. {@code "CREATION_EQUIPEMENT"}). */
	String value();
}
