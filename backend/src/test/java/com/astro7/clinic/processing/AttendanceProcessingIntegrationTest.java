package com.astro7.clinic.processing;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import com.astro7.clinic.attendance.Attendance;
import com.astro7.clinic.attendance.AttendanceService;
import com.astro7.clinic.attendance.AttendanceStatus;
import com.astro7.clinic.support.IntegrationTest;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
		"clinic.processing.max-concurrency=4",
		"clinic.processing.max-attempts=3",
		"clinic.processing.initial-backoff=30s",
		"clinic.processing.stale-after=2m",
		"clinic.protocol.read-timeout=3s" })
class AttendanceProcessingIntegrationTest extends IntegrationTest {

	private static final String PROTOCOL = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
	private static final String UUID_BODY = "{\"uuid\": \"" + PROTOCOL + "\"}";
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final List<String> VALID_CPFS = List.of("52998224725", "11144477735", "12345678909",
			"39053344705", "86288366757", "71428793860", "44867497150", "28625587887", "93541134780",
			"03914186020");

	@RegisterExtension
	static final WireMockExtension HTTPBIN = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	@Autowired
	private AttendanceDispatcher dispatcher;

	@Autowired
	private AttendanceProcessingService processingService;

	@Autowired
	private AttendanceService attendanceService;

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void protocolUrl(DynamicPropertyRegistry registry) {
		registry.add("clinic.protocol.url", () -> HTTPBIN.baseUrl() + "/uuid");
	}

	/**
	 * Uma tarefa que sobrasse de um teste gravaria num atendimento que o próximo já
	 * apagou, e ainda ocuparia uma vaga do limite de concorrência.
	 */
	@AfterEach
	void waitForInFlightProcessing() {
		await().atMost(TIMEOUT).until(() -> dispatcher.inFlight() == 0);
	}

	@Test
	void showsProtocolOnceProcessed() throws Exception {
		HTTPBIN.stubFor(get("/uuid").willReturn(okJson(UUID_BODY)));
		long id = open("52998224725");

		assertThat(dispatcher.dispatchDue()).isEqualTo(1);

		await().atMost(TIMEOUT).until(() -> statusOf(id) == AttendanceStatus.COMPLETED);
		mockMvc.perform(MockMvcRequestBuilders.get("/api/attendances/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.protocol").value(PROTOCOL));
	}

	@Test
	void keepsAttendanceForLaterWhenServiceFails() {
		HTTPBIN.stubFor(get("/uuid").willReturn(serverError()));
		long id = open("52998224725");
		LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);

		dispatcher.dispatchDue();

		Attendance attendance = awaitStatus(id, AttendanceStatus.PENDING);
		assertThat(attendance.getAttempts()).isEqualTo(1);
		assertThat(attendance.getLastError()).contains("500");
		assertThat(attendance.getNextAttemptAt()).isAfter(before.plusSeconds(25));
	}

	@Test
	void treatsResponseWithoutUuidAsFailure() {
		HTTPBIN.stubFor(get("/uuid").willReturn(okJson("{\"origin\": \"10.0.0.1\"}")));
		long id = open("52998224725");

		dispatcher.dispatchDue();

		Attendance attendance = awaitStatus(id, AttendanceStatus.PENDING);
		assertThat(attendance.getLastError()).contains("sem UUID");
		assertThat(attendance.getProtocol()).isNull();
	}

	@Test
	void doesNotRetryBeforeTheScheduledTime() {
		HTTPBIN.stubFor(get("/uuid").willReturn(serverError()));
		long id = open("52998224725");
		dispatcher.dispatchDue();
		awaitStatus(id, AttendanceStatus.PENDING);

		assertThat(dispatcher.dispatchDue()).isZero();
	}

	@Test
	void failsAfterTheLastAttempt() {
		HTTPBIN.stubFor(get("/uuid").willReturn(serverError()));
		long id = open("52998224725");

		for (int attempt = 1; attempt <= 2; attempt++) {
			dispatcher.dispatchDue();
			int expectedAttempts = attempt;
			await().atMost(TIMEOUT).until(() -> {
				Attendance current = find(id);
				return current.getStatus() == AttendanceStatus.PENDING && current.getAttempts() == expectedAttempts;
			});
			makeDueNow(id);
		}
		dispatcher.dispatchDue();

		Attendance attendance = awaitStatus(id, AttendanceStatus.FAILED);
		assertThat(attendance.getAttempts()).isEqualTo(3);
		assertThat(HTTPBIN.getAllServeEvents()).hasSize(3);
	}

	/**
	 * Quatro chamadas de 1s cada: em paralelo terminam juntas em cerca de 1s; uma
	 * esperando a outra levariam 4s.
	 */
	@Test
	void slowCallsRunInParallel() {
		HTTPBIN.stubFor(get("/uuid").willReturn(okJson(UUID_BODY).withFixedDelay(1_000)));
		openMany(4);
		long startedAt = System.nanoTime();

		assertThat(dispatcher.dispatchDue()).isEqualTo(4);

		await().atMost(TIMEOUT).until(() -> countWithStatus(AttendanceStatus.COMPLETED) == 4);
		assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofMillis(2_500));
	}

	@Test
	void timeoutOfOneCallDoesNotAffectTheOthers() {
		HTTPBIN.stubFor(get("/uuid").willReturn(okJson(UUID_BODY).withFixedDelay(5_000)));
		long slow = open(VALID_CPFS.get(1));
		dispatcher.dispatchDue();
		// Só troca a resposta depois que a chamada lenta já chegou, senão ela pegaria a rápida.
		await().atMost(TIMEOUT).until(() -> HTTPBIN.getAllServeEvents().size() == 1);
		HTTPBIN.stubFor(get("/uuid").willReturn(okJson(UUID_BODY)));
		long fast = open(VALID_CPFS.get(2));
		dispatcher.dispatchDue();

		awaitStatus(fast, AttendanceStatus.COMPLETED);
		assertThat(statusOf(slow)).isEqualTo(AttendanceStatus.PROCESSING);
		Attendance timedOut = awaitStatus(slow, AttendanceStatus.PENDING);
		assertThat(timedOut.getAttempts()).isEqualTo(1);
		assertThat(timedOut.getLastError()).contains("I/O error");
	}

	@Test
	void neverRunsMoreThanTheConcurrencyLimit() {
		HTTPBIN.stubFor(get("/uuid").willReturn(okJson(UUID_BODY).withFixedDelay(500)));
		openMany(6);

		assertThat(dispatcher.dispatchDue()).isEqualTo(4);
		assertThat(dispatcher.dispatchDue()).isZero();
		assertThat(countWithStatus(AttendanceStatus.PENDING)).isEqualTo(2);

		await().atMost(TIMEOUT).until(() -> {
			dispatcher.dispatchDue();
			return countWithStatus(AttendanceStatus.COMPLETED) == 6;
		});
	}

	@Test
	void concurrentRoundsNeverClaimTheSameAttendance() throws Exception {
		openMany(10);
		CountDownLatch start = new CountDownLatch(1);

		CompletableFuture<List<Long>> first = CompletableFuture.supplyAsync(() -> claimAfter(start));
		CompletableFuture<List<Long>> second = CompletableFuture.supplyAsync(() -> claimAfter(start));
		start.countDown();

		List<Long> claimedByFirst = first.get();
		List<Long> claimedBySecond = second.get();
		assertThat(Collections.disjoint(claimedByFirst, claimedBySecond)).isTrue();
		Set<Long> all = new HashSet<>(claimedByFirst);
		all.addAll(claimedBySecond);
		assertThat(all).hasSize(10);
	}

	@Test
	void returnsAbandonedAttendanceToTheQueue() {
		long id = open("52998224725");
		processingService.claimDue(1);
		makeProcessingOld(id);

		assertThat(processingService.releaseStale()).isEqualTo(1);

		Attendance attendance = find(id);
		assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.PENDING);
		assertThat(attendance.getLastError()).isEqualTo(AttendanceProcessingService.INTERRUPTED_REASON);
	}

	@Test
	void leavesRecentProcessingAlone() {
		long id = open("52998224725");
		processingService.claimDue(1);

		assertThat(processingService.releaseStale()).isZero();
		assertThat(statusOf(id)).isEqualTo(AttendanceStatus.PROCESSING);
	}

	@Test
	void discardsLateResultOfAnAttendanceAlreadyReturnedToTheQueue() {
		long id = open("52998224725");
		processingService.claimDue(1);
		makeProcessingOld(id);
		processingService.releaseStale();

		processingService.complete(id, PROTOCOL);

		Attendance attendance = find(id);
		assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.PENDING);
		assertThat(attendance.getProtocol()).isNull();
	}

	private List<Long> claimAfter(CountDownLatch start) {
		try {
			start.await();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
		return processingService.claimDue(10);
	}

	private long open(String cpf) {
		return attendanceService.open("Maria da Silva", cpf).getId();
	}

	private void openMany(int count) {
		IntStream.range(0, count).forEach(i -> open(VALID_CPFS.get(i)));
	}

	private Attendance awaitStatus(long id, AttendanceStatus expected) {
		await().atMost(TIMEOUT).until(() -> statusOf(id) == expected);
		return find(id);
	}

	private AttendanceStatus statusOf(long id) {
		return find(id).getStatus();
	}

	private Attendance find(long id) {
		return attendanceRepository.findById(id).orElseThrow();
	}

	private long countWithStatus(AttendanceStatus status) {
		return attendanceRepository.findAll().stream().filter(a -> a.getStatus() == status).count();
	}

}
