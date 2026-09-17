package com.astro7.clinic.cpf;

import java.util.regex.Pattern;

/**
 * Regras do CPF: dígitos verificadores, normalização e máscara para exibição.
 */
public final class Cpf {

	public static final int LENGTH = 11;

	private static final Pattern NON_DIGIT = Pattern.compile("\\D");
	private static final Pattern FORMATTED_OR_DIGITS = Pattern.compile("\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}");

	private Cpf() {
	}

	public static String digitsOf(String value) {
		return NON_DIGIT.matcher(value).replaceAll("");
	}

	/**
	 * Aceita só dígitos ou o formato 000.000.000-00. Sequências repetidas
	 * (111.111.111-11) passam no cálculo dos dígitos, mas não são CPFs emitidos.
	 */
	public static boolean isValid(String value) {
		if (value == null || !FORMATTED_OR_DIGITS.matcher(value.strip()).matches()) {
			return false;
		}
		String digits = digitsOf(value);
		if (digits.chars().distinct().count() == 1) {
			return false;
		}
		return checkDigit(digits, 9) == digitAt(digits, 9)
				&& checkDigit(digits, 10) == digitAt(digits, 10);
	}

	/**
	 * Mostra só o miolo: ***.456.789-**. O começo e o fim ficam ocultos
	 * porque, com o nome ao lado, identificariam o paciente.
	 */
	public static String mask(String digits) {
		if (digits.length() != LENGTH) {
			throw new IllegalArgumentException("CPF deve ter " + LENGTH + " dígitos");
		}
		return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
	}

	private static int checkDigit(String digits, int position) {
		int sum = 0;
		for (int i = 0; i < position; i++) {
			sum += digitAt(digits, i) * (position + 1 - i);
		}
		int remainder = (sum * 10) % 11;
		return remainder == 10 ? 0 : remainder;
	}

	private static int digitAt(String digits, int index) {
		return digits.charAt(index) - '0';
	}

}
