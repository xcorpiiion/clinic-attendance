package com.astro7.clinic.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.astro7.clinic.attendance.AttendanceNotFoundException;
import com.astro7.clinic.attendance.OpenAttendanceExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todo erro sai como ProblemDetail (RFC 9457). Os erros de validação levam, em
 * {@code errors}, a mensagem de cada campo, para o front mostrar ao lado dele.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
	private static final Set<String> REQUIRED_CONSTRAINTS = Set.of("NotBlank", "NotNull", "NotEmpty");

	@ExceptionHandler(AttendanceNotFoundException.class)
	ProblemDetail handleNotFound(AttendanceNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Atendimento não encontrado", ex.getMessage());
	}

	@ExceptionHandler(OpenAttendanceExistsException.class)
	ProblemDetail handleOpenAttendanceExists(OpenAttendanceExistsException ex) {
		return problem(HttpStatus.CONFLICT, "Atendimento em andamento", ex.getMessage());
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Erro inesperado ao atender a requisição", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
				"Não foi possível concluir a operação. Tente novamente em instantes.");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			// Um campo vazio também viola tamanho e formato; "informe o campo" é a mensagem útil.
			if (REQUIRED_CONSTRAINTS.contains(fieldError.getCode())) {
				errors.put(fieldError.getField(), fieldError.getDefaultMessage());
			}
			else {
				errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
			}
		}
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "Corrija os campos indicados.");
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
