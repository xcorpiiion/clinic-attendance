package com.astro7.clinic.processing;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.astro7.clinic.attendance.Attendance;
import com.astro7.clinic.attendance.AttendanceNotFoundException;
import com.astro7.clinic.attendance.AttendanceRepository;
import com.astro7.clinic.attendance.AttendanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As transações do processamento. Cada uma é curta e nenhuma contém a chamada
 * externa: segurar a conexão com o banco durante uma resposta lenta esgotaria o
 * pool, e aí um atendimento lento travaria todos os outros.
 */
@Service
public class AttendanceProcessingService {

	private static final Logger log = LoggerFactory.getLogger(AttendanceProcessingService.class);

	static final String INTERRUPTED_REASON = "Processamento interrompido antes de terminar";
	private static final Limit STALE_BATCH = Limit.of(100);

	private final AttendanceRepository repository;
	private final RetryPolicy retryPolicy;
	private final Duration staleAfter;
	private final Clock clock;

	public AttendanceProcessingService(AttendanceRepository repository, RetryPolicy retryPolicy,
			ProcessingProperties properties, Clock clock) {
		this.repository = repository;
		this.retryPolicy = retryPolicy;
		this.staleAfter = properties.staleAfter();
		this.clock = clock;
	}

	/**
	 * Reserva até {@code limit} atendimentos vencidos, passando-os para PROCESSING.
	 */
	@Transactional
	public List<Long> claimDue(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		LocalDateTime now = now();
		List<Attendance> due = repository.lockDueForProcessing(now, limit);
		due.forEach(attendance -> attendance.startProcessing(now));
		return due.stream().map(Attendance::getId).toList();
	}

	@Transactional
	public void complete(long id, String protocol) {
		Attendance attendance = findProcessing(id);
		if (attendance != null) {
			attendance.complete(protocol, now());
		}
	}

	@Transactional
	public void registerFailure(long id, String reason) {
		Attendance attendance = findProcessing(id);
		if (attendance != null) {
			applyFailure(attendance, reason, now());
		}
	}

	/**
	 * Devolve à fila, como uma tentativa que falhou, os atendimentos que ficaram em
	 * PROCESSING por tempo demais: o serviço caiu, ou foi reiniciado, no meio da chamada.
	 */
	@Transactional
	public int releaseStale() {
		LocalDateTime now = now();
		List<Attendance> stale = repository.findByStatusAndUpdatedAtBefore(
				AttendanceStatus.PROCESSING, now.minus(staleAfter), STALE_BATCH);
		stale.forEach(attendance -> applyFailure(attendance, INTERRUPTED_REASON, now));
		return stale.size();
	}

	private void applyFailure(Attendance attendance, String reason, LocalDateTime now) {
		Optional<Duration> delay = retryPolicy.delayBeforeNextAttempt(attendance.getAttempts());
		if (delay.isPresent()) {
			attendance.scheduleRetry(reason, now.plus(delay.get()), now);
		}
		else {
			attendance.fail(reason, now);
			log.warn("Atendimento {} falhou após {} tentativas: {}", attendance.getId(), attendance.getAttempts(),
					reason);
		}
	}

	/**
	 * Devolve null quando o atendimento já saiu de PROCESSING: a recuperação de
	 * abandonados chegou antes. O resultado desta tentativa é descartado.
	 */
	private Attendance findProcessing(long id) {
		Attendance attendance = repository.findById(id).orElseThrow(() -> new AttendanceNotFoundException(id));
		if (attendance.getStatus() != AttendanceStatus.PROCESSING) {
			log.warn("Atendimento {} está em {}; resultado da tentativa descartado", id, attendance.getStatus());
			return null;
		}
		return attendance;
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

}
