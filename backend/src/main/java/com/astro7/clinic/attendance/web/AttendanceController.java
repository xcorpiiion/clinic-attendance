package com.astro7.clinic.attendance.web;

import java.net.URI;

import com.astro7.clinic.attendance.Attendance;
import com.astro7.clinic.attendance.AttendanceService;
import com.astro7.clinic.web.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Atendimentos")
@RestController
@RequestMapping(path = "/api/attendances", produces = MediaType.APPLICATION_JSON_VALUE)
class AttendanceController {

	private final AttendanceService attendanceService;

	AttendanceController(AttendanceService attendanceService) {
		this.attendanceService = attendanceService;
	}

	@Operation(summary = "Abre um atendimento, que aguarda o protocolo como PENDING")
	@ApiResponse(responseCode = "201", description = "Atendimento aberto")
	@ApiResponse(responseCode = "400", description = "Dados inválidos; o campo errors traz a mensagem de cada um",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "409", description = "O CPF já tem um atendimento em andamento",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<AttendanceResponse> open(@Valid @RequestBody OpenAttendanceRequest request) {
		return created(attendanceService.open(request.patientName(), request.cpf()));
	}

	@Operation(summary = "Abre um novo atendimento para o paciente de um atendimento já encerrado",
			description = "Aceito só quando o atendimento informado está COMPLETED ou FAILED.")
	@ApiResponse(responseCode = "201", description = "Novo atendimento aberto")
	@ApiResponse(responseCode = "404", description = "Atendimento não encontrado",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@ApiResponse(responseCode = "409", description = "O paciente ainda tem um atendimento em andamento",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@PostMapping("/{id}/renewals")
	ResponseEntity<AttendanceResponse> renew(@PathVariable long id) {
		return created(attendanceService.renew(id));
	}

	@Operation(summary = "Consulta o atendimento; o protocolo aparece quando o status é COMPLETED")
	@ApiResponse(responseCode = "200", description = "Atendimento encontrado")
	@ApiResponse(responseCode = "404", description = "Atendimento não encontrado",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ApiProblem.class)))
	@GetMapping("/{id}")
	AttendanceResponse findById(@PathVariable long id) {
		return AttendanceResponse.from(attendanceService.findById(id));
	}

	private static ResponseEntity<AttendanceResponse> created(Attendance attendance) {
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/api/attendances/{id}")
				.buildAndExpand(attendance.getId())
				.toUri();
		return ResponseEntity.created(location).body(AttendanceResponse.from(attendance));
	}

}
