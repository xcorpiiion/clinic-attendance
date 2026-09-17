package com.astro7.clinic.attendance.web;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.astro7.clinic.attendance.Attendance;
import com.astro7.clinic.attendance.AttendanceStatus;
import com.astro7.clinic.cpf.Cpf;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * O CPF sai mascarado: a API não tem autenticação, e o id é sequencial.
 */
public record AttendanceResponse(

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "42")
		long id,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Maria da Silva")
		String patientName,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "***.982.247-**")
		String maskedCpf,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
		AttendanceStatus status,

		@Schema(description = "Presente só quando o status é COMPLETED",
				example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
		@JsonInclude(JsonInclude.Include.NON_NULL)
		String protocol,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
		OffsetDateTime createdAt,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
		OffsetDateTime updatedAt) {

	static AttendanceResponse from(Attendance attendance) {
		return new AttendanceResponse(
				attendance.getId(),
				attendance.getPatientName(),
				Cpf.mask(attendance.getCpf()),
				attendance.getStatus(),
				attendance.getProtocol(),
				attendance.getCreatedAt().atOffset(ZoneOffset.UTC),
				attendance.getUpdatedAt().atOffset(ZoneOffset.UTC));
	}

}
