package com.astro7.clinic.attendance;

public class OpenAttendanceExistsException extends RuntimeException {

	private static final String MESSAGE = "Já existe um atendimento em andamento para este CPF";

	public OpenAttendanceExistsException() {
		super(MESSAGE);
	}

	public OpenAttendanceExistsException(Throwable cause) {
		super(MESSAGE, cause);
	}

}
