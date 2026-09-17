package com.astro7.clinic.web;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A forma do corpo de erro, só para o OpenAPI. Quem responde é o {@code ProblemDetail}
 * do Spring, mas o schema que o springdoc deriva dele mostra um campo {@code properties}
 * que não existe no JSON e omite o {@code errors}, que existe.
 */
@Schema(name = "ApiProblem", description = "Erro no formato RFC 9457")
public record ApiProblem(

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Dados inválidos")
		String title,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "400")
		int status,

		@Schema(example = "Corrija os campos indicados.")
		String detail,

		@Schema(example = "/api/attendances")
		String instance,

		@Schema(description = "Só em erros de validação: a mensagem de cada campo inválido",
				example = "{\"cpf\": \"CPF inválido\"}")
		Map<String, String> errors) {
}
