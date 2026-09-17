package com.astro7.clinic.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * {@code fixedDelay}, e não {@code fixedRate}: a próxima rodada começa um intervalo
 * depois de a anterior terminar, então rodadas nunca se sobrepõem. O despacho só
 * reserva e entrega os atendimentos a outras threads, então a rodada é curta mesmo
 * com o serviço externo lento.
 * <p>
 * Desligável por {@code clinic.processing.enabled}: os testes disparam as rodadas
 * na mão, para não disputar os atendimentos com o agendamento.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty("clinic.processing.enabled")
class AttendanceProcessingScheduler {

	private static final Logger log = LoggerFactory.getLogger(AttendanceProcessingScheduler.class);

	private final AttendanceDispatcher dispatcher;
	private final AttendanceProcessingService processingService;

	AttendanceProcessingScheduler(AttendanceDispatcher dispatcher, AttendanceProcessingService processingService) {
		this.dispatcher = dispatcher;
		this.processingService = processingService;
	}

	@Scheduled(fixedDelayString = "${clinic.processing.poll-interval}")
	void dispatchDue() {
		int dispatched = dispatcher.dispatchDue();
		if (dispatched > 0) {
			log.debug("{} atendimento(s) enviados ao processamento", dispatched);
		}
	}

	@Scheduled(fixedDelayString = "${clinic.processing.stale-check-interval}",
			initialDelayString = "${clinic.processing.stale-check-interval}")
	void releaseStale() {
		int released = processingService.releaseStale();
		if (released > 0) {
			log.warn("{} atendimento(s) abandonados em PROCESSING devolvidos à fila", released);
		}
	}

}
