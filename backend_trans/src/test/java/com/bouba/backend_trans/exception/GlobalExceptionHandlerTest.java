package com.bouba.backend_trans.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Le format des réponses d'erreur (§8.6 : code HTTP + {@link ErrorResponse})
 * doit rester stable pour le frontend, module par module d'exception métier.
 */
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void une_erreur_de_validation_produit_un_400_avec_le_detail_par_champ() throws Exception {
		BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "equipementRequest");
		bindingResult.addError(new FieldError("equipementRequest", "nom", "ne doit pas être vide"));
		bindingResult.addError(new FieldError("equipementRequest", "adresseIp", "ne doit pas être vide"));
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parametreFictif(), bindingResult);

		ResponseEntity<ErrorResponse> reponse = handler.handleValidation(ex);

		assertThat(reponse.getStatusCode().value()).isEqualTo(400);
		ErrorResponse corps = reponse.getBody();
		assertThat(corps.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(corps.getMessage()).isEqualTo("Validation failed.");
		assertThat(corps.getErrors())
				.containsEntry("nom", "ne doit pas être vide")
				.containsEntry("adresseIp", "ne doit pas être vide");
		assertThat(corps.getTimestamp()).isNotNull();
	}

	@Test
	void un_conflit_d_etat_produit_un_409_sans_detail_par_champ() {
		IllegalStateException ex = new IllegalStateException("Une alerte est déjà active pour cet équipement.");

		ResponseEntity<ErrorResponse> reponse = handler.handleConflict(ex);

		assertThat(reponse.getStatusCode().value()).isEqualTo(409);
		ErrorResponse corps = reponse.getBody();
		assertThat(corps.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
		assertThat(corps.getMessage()).isEqualTo("Une alerte est déjà active pour cet équipement.");
		assertThat(corps.getErrors()).isNull();
	}

	@Test
	void un_argument_illegal_produit_un_401() {
		IllegalArgumentException ex = new IllegalArgumentException("Identifiants invalides.");

		ResponseEntity<ErrorResponse> reponse = handler.handleUnauthorized(ex);

		assertThat(reponse.getStatusCode().value()).isEqualTo(401);
		ErrorResponse corps = reponse.getBody();
		assertThat(corps.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
		assertThat(corps.getMessage()).isEqualTo("Identifiants invalides.");
	}

	@Test
	void un_compte_verrouille_produit_un_423() {
		AccountLockedException ex = new AccountLockedException("Compte verrouillé, réessayez dans 5 minutes.");

		ResponseEntity<ErrorResponse> reponse = handler.handleAccountLocked(ex);

		assertThat(reponse.getStatusCode().value()).isEqualTo(423);
		ErrorResponse corps = reponse.getBody();
		assertThat(corps.getStatus()).isEqualTo(HttpStatus.LOCKED.value());
		assertThat(corps.getMessage()).isEqualTo("Compte verrouillé, réessayez dans 5 minutes.");
	}

	/** Un {@link MethodArgumentNotValidException} exige un {@link MethodParameter} réel. */
	private MethodParameter parametreFictif() throws NoSuchMethodException {
		Method methode = GlobalExceptionHandlerTest.class.getDeclaredMethod("cibleFictive", Object.class);
		return new MethodParameter(methode, 0);
	}

	@SuppressWarnings("unused")
	private void cibleFictive(Object corps) {
	}
}
