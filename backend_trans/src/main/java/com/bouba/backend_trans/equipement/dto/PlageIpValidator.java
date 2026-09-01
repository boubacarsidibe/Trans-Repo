package com.bouba.backend_trans.equipement.dto;

import com.bouba.backend_trans.equipement.scan.PlageIpUtils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PlageIpValidator implements ConstraintValidator<ValidPlageIp, ScanRequest> {

	@Override
	public boolean isValid(ScanRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		String ipDebut = request.getIpDebut();
		String ipFin = request.getIpFin();
		if (ipDebut == null || ipDebut.isBlank() || ipFin == null || ipFin.isBlank()) {
			// Champs vides : couvert par @NotBlank sur ipDebut/ipFin, pas ici.
			return true;
		}

		context.disableDefaultConstraintViolation();

		if (!PlageIpUtils.estFormatIpv4Valide(ipDebut)) {
			violation(context, "ipDebut", "Adresse IP de début invalide : " + ipDebut);
			return false;
		}
		if (!PlageIpUtils.estFormatIpv4Valide(ipFin)) {
			violation(context, "ipFin", "Adresse IP de fin invalide : " + ipFin);
			return false;
		}

		long debut = PlageIpUtils.versEntierNonSigne(ipDebut);
		long fin = PlageIpUtils.versEntierNonSigne(ipFin);

		if (fin < debut) {
			violation(context, "ipFin", "L'adresse de fin doit être postérieure ou égale à l'adresse de début.");
			return false;
		}

		long taillePlage = fin - debut + 1;
		if (taillePlage > ScanRequest.TAILLE_MAX_PLAGE) {
			violation(context, "ipFin", "La plage dépasse la taille maximale autorisée ("
					+ ScanRequest.TAILLE_MAX_PLAGE + " adresses, l'équivalent d'un /24).");
			return false;
		}

		return true;
	}

	private void violation(ConstraintValidatorContext context, String champ, String message) {
		context.buildConstraintViolationWithTemplate(message)
				.addPropertyNode(champ)
				.addConstraintViolation();
	}
}
