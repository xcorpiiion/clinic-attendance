package com.astro7.clinic.cpf;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valor ausente é aceito aqui de propósito: obrigatoriedade é do {@code @NotBlank},
 * e assim o campo vazio recebe uma mensagem só, e não duas.
 */
public class CpfValidator implements ConstraintValidator<ValidCpf, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || value.isBlank() || Cpf.isValid(value);
	}

}
