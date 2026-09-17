package com.astro7.clinic.attendance;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AttendanceTest {

	private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 9, 16, 12, 0);
	private static final LocalDateTime LATER = OPENED_AT.plusSeconds(3);

	@Test
	void opensPendingAndDueImmediately() {
		Attendance attendance = newAttendance();

		assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.PENDING);
		assertThat(attendance.getAttempts()).isZero();
		assertThat(attendance.getNextAttemptAt()).isEqualTo(OPENED_AT);
		assertThat(attendance.getProtocol()).isNull();
	}

	@Test
	void countsTheAttemptWhenProcessingStarts() {
		Attendance attendance = newAttendance();

		attendance.startProcessing(LATER);

		assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.PROCESSING);
		assertThat(attendance.getAttempts()).isEqualTo(1);
		assertThat(attendance.getUpdatedAt()).isEqualTo(LATER);
	}

	@Test
	void completesWithProtocolAndClearsPreviousError() {
		Attendance attendance = newAttendance();
		attendance.startProcessing(OPENED_AT);
		attendance.scheduleRetry("timeout", OPENED_AT, OPENED_AT);
		attendance.startProcessing(OPENED_AT);

		attendance.complete("3fa85f64-5717-4562-b3fc-2c963f66afa6", LATER);

		assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.COMPLETED);
		assertThat(attendance.getProtocol()).isEqualTo("3fa85f64-5717-4562-b3fc-2c963f66afa6");
		assertThat(attendance.getLastError()).isNull();
	}

	@Test
	void goesBackToPendingWhenRetryIsScheduled() {
		Attendance attendance = newAttendance();
		attendance.startProcessing(OPENED_AT);
		LocalDateTime nextAttempt = LATER.plusSeconds(5);

		attendance.scheduleRetry("HTTP 500", nextAttempt, LATER);

		assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.PENDING);
		assertThat(attendance.getNextAttemptAt()).isEqualTo(nextAttempt);
		assertThat(attendance.getLastError()).isEqualTo("HTTP 500");
	}

	@Test
	void failsKeepingTheReason() {
		Attendance attendance = newAttendance();
		attendance.startProcessing(OPENED_AT);

		attendance.fail("HTTP 503", LATER);

		assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.FAILED);
		assertThat(attendance.getLastError()).isEqualTo("HTTP 503");
	}

	@Test
	void truncatesLongErrorToFitTheColumn() {
		Attendance attendance = newAttendance();
		attendance.startProcessing(OPENED_AT);

		attendance.fail("x".repeat(Attendance.LAST_ERROR_MAX_LENGTH + 50), LATER);

		assertThat(attendance.getLastError()).hasSize(Attendance.LAST_ERROR_MAX_LENGTH);
	}

	@Test
	void refusesToCompleteWithoutProcessing() {
		Attendance attendance = newAttendance();

		assertThatIllegalStateException().isThrownBy(() -> attendance.complete("protocol", LATER));
	}

	@Test
	void refusesToStartTwice() {
		Attendance attendance = newAttendance();
		attendance.startProcessing(OPENED_AT);

		assertThatIllegalStateException().isThrownBy(() -> attendance.startProcessing(LATER));
	}

	private static Attendance newAttendance() {
		return Attendance.open("Maria da Silva", "52998224725", OPENED_AT);
	}

}
