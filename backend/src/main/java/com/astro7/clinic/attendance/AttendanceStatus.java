package com.astro7.clinic.attendance;

import java.util.Set;

public enum AttendanceStatus {

	/** Aguardando a próxima tentativa de obter o protocolo. */
	PENDING,

	/** Reservado por uma rodada do processamento, com a chamada externa em andamento. */
	PROCESSING,

	COMPLETED,

	/** As tentativas acabaram sem protocolo. */
	FAILED;

	/** Enquanto aberto, o CPF não pode abrir outro atendimento. */
	public static final Set<AttendanceStatus> OPEN = Set.of(PENDING, PROCESSING);

}
