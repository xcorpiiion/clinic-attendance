package com.astro7.clinic.attendance.web;

import com.astro7.clinic.attendance.Attendance;
import com.astro7.clinic.attendance.AttendanceStatus;
import com.astro7.clinic.processing.AttendanceProcessingService;
import com.astro7.clinic.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttendanceApiIntegrationTest extends IntegrationTest {

	private static final String VALID_CPF = "52998224725";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AttendanceProcessingService processingService;

	@Test
	void opensPendingAttendanceWithNormalizedData() throws Exception {
		mockMvc.perform(openRequest("  Maria   da Silva ", "529.982.247-25"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.patientName").value("Maria da Silva"))
				.andExpect(jsonPath("$.maskedCpf").value("***.982.247-**"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.protocol").doesNotExist());

		Attendance saved = attendanceRepository.findAll().getFirst();
		assertThat(saved.getCpf()).isEqualTo(VALID_CPF);
		assertThat(saved.getStatus()).isEqualTo(AttendanceStatus.PENDING);
	}

	@Test
	void pointsLocationToTheNewAttendance() throws Exception {
		String location = mockMvc.perform(openRequest("Maria da Silva", VALID_CPF))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

		long id = attendanceRepository.findAll().getFirst().getId();
		assertThat(location).endsWith("/api/attendances/" + id);
	}

	@Test
	void findsAttendanceById() throws Exception {
		long id = openAttendance();

		mockMvc.perform(get("/api/attendances/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.createdAt").isString());
	}

	@Test
	void reportsEachInvalidField() throws Exception {
		mockMvc.perform(openRequest("", "123.456.789-00"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Dados inválidos"))
				.andExpect(jsonPath("$.errors.patientName").value("Informe o nome"))
				.andExpect(jsonPath("$.errors.cpf").value("CPF inválido"));
	}

	@Test
	void rejectsNameWithDigits() throws Exception {
		mockMvc.perform(openRequest("Maria 2", VALID_CPF))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.patientName").value("O nome deve conter apenas letras"));
	}

	@Test
	void rejectsMalformedBody() throws Exception {
		mockMvc.perform(post("/api/attendances").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void answersNotFoundAsProblemDetail() throws Exception {
		mockMvc.perform(get("/api/attendances/{id}", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(jsonPath("$.title").value("Atendimento não encontrado"));
	}

	@Test
	void refusesSecondOpenAttendanceForSameCpf() throws Exception {
		openAttendance();

		mockMvc.perform(openRequest("Maria da Silva", "529.982.247-25"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Já existe um atendimento em andamento para este CPF"));
	}

	@Test
	void allowsNewAttendanceOnceThePreviousIsCompleted() throws Exception {
		long first = openAttendance();
		processingService.claimDue(1);
		processingService.complete(first, "3fa85f64-5717-4562-b3fc-2c963f66afa6");

		mockMvc.perform(openRequest("Maria da Silva", VALID_CPF))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void allowsCorsOnlyFromTheFrontend() throws Exception {
		mockMvc.perform(options("/api/attendances")
						.header(HttpHeaders.ORIGIN, "http://localhost:3000")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));

		mockMvc.perform(options("/api/attendances")
						.header(HttpHeaders.ORIGIN, "http://evil.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden());
	}

	private long openAttendance() throws Exception {
		mockMvc.perform(openRequest("Maria da Silva", VALID_CPF)).andExpect(status().isCreated());
		return attendanceRepository.findAll().getFirst().getId();
	}

	private static RequestBuilder openRequest(String name, String cpf) {
		return post("/api/attendances")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"patientName": "%s", "cpf": "%s"}
						""".formatted(name, cpf));
	}

}
