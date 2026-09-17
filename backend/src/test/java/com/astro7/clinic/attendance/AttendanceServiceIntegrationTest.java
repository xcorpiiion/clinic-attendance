package com.astro7.clinic.attendance;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import com.astro7.clinic.processing.AttendanceProcessingService;
import com.astro7.clinic.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AttendanceServiceIntegrationTest extends IntegrationTest {

	private static final String CPF = "52998224725";

	@Autowired
	private AttendanceService attendanceService;

	@Autowired
	private AttendanceProcessingService processingService;

	@Test
	void databaseRefusesSecondOpenAttendanceEvenWithoutTheServiceCheck() {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		attendanceRepository.saveAndFlush(Attendance.open("Maria da Silva", CPF, now));

		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
				() -> attendanceRepository.saveAndFlush(Attendance.open("Maria da Silva", CPF, now)));
	}

	@Test
	void onlyOneOfSimultaneousRequestsForTheSameCpfSucceeds() {
		int requests = 8;
		CountDownLatch start = new CountDownLatch(1);

		List<CompletableFuture<Boolean>> results = IntStream.range(0, requests)
				.mapToObj(i -> CompletableFuture.supplyAsync(() -> tryOpenAfter(start)))
				.toList();
		start.countDown();

		long succeeded = results.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();
		assertThat(succeeded).isEqualTo(1);
		assertThat(attendanceRepository.count()).isEqualTo(1);
	}

	@Test
	void allowsNewAttendanceAfterTheLastOneFailed() {
		long first = attendanceService.open("Maria da Silva", CPF).getId();
		// Com o limite padrão, de 5 tentativas, a quinta falha é a definitiva.
		for (int attempt = 1; attempt <= 5; attempt++) {
			makeDueNow(first);
			processingService.claimDue(1);
			processingService.registerFailure(first, "HTTP 500");
		}
		assertThat(attendanceRepository.findById(first).orElseThrow().getStatus())
				.isEqualTo(AttendanceStatus.FAILED);

		Attendance second = attendanceService.open("Maria da Silva", CPF);

		assertThat(second.getStatus()).isEqualTo(AttendanceStatus.PENDING);
	}

	private boolean tryOpenAfter(CountDownLatch start) {
		try {
			start.await();
			attendanceService.open("Maria da Silva", CPF);
			return true;
		}
		catch (OpenAttendanceExistsException ex) {
			return false;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

}
