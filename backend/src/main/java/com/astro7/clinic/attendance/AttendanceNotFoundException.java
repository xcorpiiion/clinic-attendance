package com.astro7.clinic.attendance;

public class AttendanceNotFoundException extends RuntimeException {

	public AttendanceNotFoundException(long id) {
		super("Atendimento " + id + " não encontrado");
	}

}
