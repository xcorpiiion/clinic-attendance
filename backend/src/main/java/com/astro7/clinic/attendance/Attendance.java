package com.astro7.clinic.attendance;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * As mudanças de estado só acontecem pelos métodos desta classe, e cada uma confere
 * de qual estado parte. Os horários são UTC e chegam de fora, do {@link java.time.Clock}.
 */
@Entity
@Table(name = "attendance")
public class Attendance {

	static final int LAST_ERROR_MAX_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "patient_name", nullable = false, length = 150)
	private String patientName;

	@Column(nullable = false, length = 11)
	private String cpf;

	// Sem o JdbcTypeCode, o Hibernate espera uma coluna ENUM do MySQL e a validação do schema falha.
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private AttendanceStatus status;

	@Column(length = 100)
	private String protocol;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at", nullable = false)
	private LocalDateTime nextAttemptAt;

	@Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
	private String lastError;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Version
	private long version;

	protected Attendance() {
	}

	public static Attendance open(String patientName, String cpf, LocalDateTime now) {
		Attendance attendance = new Attendance();
		attendance.patientName = patientName;
		attendance.cpf = cpf;
		attendance.status = AttendanceStatus.PENDING;
		attendance.nextAttemptAt = now;
		attendance.createdAt = now;
		attendance.updatedAt = now;
		return attendance;
	}

	public void startProcessing(LocalDateTime now) {
		requireStatus(AttendanceStatus.PENDING);
		status = AttendanceStatus.PROCESSING;
		attempts++;
		updatedAt = now;
	}

	public void complete(String protocol, LocalDateTime now) {
		requireStatus(AttendanceStatus.PROCESSING);
		this.protocol = protocol;
		status = AttendanceStatus.COMPLETED;
		lastError = null;
		updatedAt = now;
	}

	public void scheduleRetry(String reason, LocalDateTime nextAttemptAt, LocalDateTime now) {
		requireStatus(AttendanceStatus.PROCESSING);
		status = AttendanceStatus.PENDING;
		this.nextAttemptAt = nextAttemptAt;
		lastError = truncate(reason);
		updatedAt = now;
	}

	public void fail(String reason, LocalDateTime now) {
		requireStatus(AttendanceStatus.PROCESSING);
		status = AttendanceStatus.FAILED;
		lastError = truncate(reason);
		updatedAt = now;
	}

	private void requireStatus(AttendanceStatus expected) {
		if (status != expected) {
			throw new IllegalStateException(
					"Atendimento %d está em %s, e a operação exige %s".formatted(id, status, expected));
		}
	}

	private static String truncate(String reason) {
		if (reason == null || reason.length() <= LAST_ERROR_MAX_LENGTH) {
			return reason;
		}
		return reason.substring(0, LAST_ERROR_MAX_LENGTH);
	}

	public Long getId() {
		return id;
	}

	public String getPatientName() {
		return patientName;
	}

	public String getCpf() {
		return cpf;
	}

	public AttendanceStatus getStatus() {
		return status;
	}

	public String getProtocol() {
		return protocol;
	}

	public int getAttempts() {
		return attempts;
	}

	public LocalDateTime getNextAttemptAt() {
		return nextAttemptAt;
	}

	public String getLastError() {
		return lastError;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

}
