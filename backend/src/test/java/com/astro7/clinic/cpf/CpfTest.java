package com.astro7.clinic.cpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CpfTest {

	@ParameterizedTest
	@ValueSource(strings = { "52998224725", "529.982.247-25", " 529.982.247-25 ", "11144477735", "12345678909" })
	void acceptsValidCpf(String value) {
		assertThat(Cpf.isValid(value)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "52998224724", "52998224715", "1234567890", "123456789012", "529.982.247.25",
			"529 982 247 25", "5299822472a" })
	void rejectsWrongCheckDigitsOrFormat(String value) {
		assertThat(Cpf.isValid(value)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "00000000000", "11111111111", "999.999.999-99" })
	void rejectsRepeatedDigitsEvenThoughCheckDigitsMatch(String value) {
		assertThat(Cpf.isValid(value)).isFalse();
	}

	@ParameterizedTest
	@NullAndEmptySource
	void rejectsMissingValue(String value) {
		assertThat(Cpf.isValid(value)).isFalse();
	}

	@Test
	void keepsOnlyDigits() {
		assertThat(Cpf.digitsOf("529.982.247-25")).isEqualTo("52998224725");
	}

	@Test
	void masksBeginningAndEnd() {
		assertThat(Cpf.mask("52998224725")).isEqualTo("***.982.247-**");
	}

	@Test
	void refusesToMaskIncompleteCpf() {
		assertThatIllegalArgumentException().isThrownBy(() -> Cpf.mask("5299822472"));
	}

}
