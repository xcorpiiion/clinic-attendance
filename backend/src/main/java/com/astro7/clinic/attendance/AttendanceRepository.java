package com.astro7.clinic.attendance;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

	boolean existsByCpfAndStatusIn(String cpf, Collection<AttendanceStatus> statuses);

	/**
	 * Trava as linhas devolvidas até o fim da transação. O SKIP LOCKED faz uma rodada
	 * concorrente (outra instância do serviço) pular as linhas já travadas em vez de
	 * esperar por elas, então cada atendimento é reservado por uma rodada só.
	 */
	@Query(value = """
			SELECT * FROM attendance
			WHERE status = 'PENDING' AND next_attempt_at <= :now
			ORDER BY next_attempt_at, id
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<Attendance> lockDueForProcessing(LocalDateTime now, int limit);

	List<Attendance> findByStatusAndUpdatedAtBefore(AttendanceStatus status, LocalDateTime threshold, Limit limit);

}
