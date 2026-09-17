package com.astro7.clinic.attendance.web;

import com.astro7.clinic.cpf.ValidCpf;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpenAttendanceRequest(

		@Schema(example = "Maria da Silva")
		@NotBlank(message = "Informe o nome")
		@Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres")
		// Vazio passa aqui para o @NotBlank dar a única mensagem; espaços nas pontas são removidos depois.
		@Pattern(regexp = "^\\s*(\\p{L}[\\p{L} .'-]*)?$", message = "O nome deve conter apenas letras")
		String patientName,

		@Schema(description = "Só dígitos ou no formato 000.000.000-00", example = "52998224725")
		@NotBlank(message = "Informe o CPF")
		@ValidCpf
		String cpf) {
}
