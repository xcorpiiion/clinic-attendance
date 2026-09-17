package com.astro7.clinic.processing;

import java.time.Duration;

import com.astro7.clinic.attendance.AttendanceStatus;
import com.astro7.clinic.support.IntegrationTest;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O fluxo com o agendamento ligado, sem nenhuma rodada disparada na mão.
 * <p>
 * O limite de abandono aqui é menor que a demora da resposta de propósito, para a
 * recuperação agir sobre uma chamada que ainda está em andamento.
 */
@TestPropertySource(properties = {
		"clinic.processing.enabled=true",
		"clinic.processing.poll-interval=200ms",
		"clinic.processing.stale-check-interval=200ms",
		"clinic.processing.stale-after=1s",
		"clinic.protocol.read-timeout=3s" })
class ScheduledProcessingIntegrationTest extends IntegrationTest {

	private static final String UUID_BODY = "{\"uuid\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\"}";
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	@RegisterExtension
	static final WireMockExtension HTTPBIN = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AttendanceDispatcher dispatcher;

	@DynamicPropertySource
	static void protocolUrl(DynamicPropertyRegistry registry) {
		registry.add("clinic.protocol.url", () -> HTTPBIN.baseUrl() + "/uuid");
	}

	@AfterEach
	void waitForInFlightProcessing() {
		await().atMost(TIMEOUT).until(() -> dispatcher.inFlight() == 0);
	}

	@Test
	void protocolArrivesWithoutAnyManualRound() throws Exception {
		HTTPBIN.stubFor(get("/uuid").willReturn(okJson(UUID_BODY)));

		openThroughApi();

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(attendanceRepository.findAll())
				.singleElement()
				.satisfies(attendance -> {
					assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.COMPLETED);
					assertThat(attendance.getProtocol()).isNotBlank();
				}));
	}

	@Test
	void attendanceStuckInProcessingGoesBackToTheQueue() throws Exception {
		HTTPBIN.stubFor(get("/uuid").willReturn(okJson(UUID_BODY).withFixedDelay(2_500)));

		openThroughApi();

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(attendanceRepository.findAll())
				.singleElement()
				.satisfies(attendance -> {
					assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.PENDING);
					assertThat(attendance.getLastError()).isEqualTo(AttendanceProcessingService.INTERRUPTED_REASON);
				}));
	}

	private void openThroughApi() throws Exception {
		mockMvc.perform(post("/api/attendances")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"patientName\": \"Maria da Silva\", \"cpf\": \"52998224725\"}"))
				.andExpect(status().isCreated());
	}

}
