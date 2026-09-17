package com.astro7.clinic.processing;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

import com.astro7.clinic.protocol.ProtocolClient;
import com.astro7.clinic.protocol.ProtocolUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Reserva os atendimentos vencidos e processa cada um na sua própria thread, sem
 * esperar pelos outros. Com virtual threads ligadas, o executor da aplicação cria
 * uma thread por tarefa, e uma chamada externa lenta ocupa só a dela.
 * <p>
 * O semáforo limita quantos estão em andamento ao mesmo tempo. Uma rodada reserva
 * no máximo as vagas livres, e o que não coube fica PENDING para a próxima, em vez de
 * se acumular em memória.
 */
@Component
public class AttendanceDispatcher {

	private static final Logger log = LoggerFactory.getLogger(AttendanceDispatcher.class);

	private final AttendanceProcessingService processingService;
	private final ProtocolClient protocolClient;
	private final TaskExecutor taskExecutor;
	private final Semaphore slots;
	private final int maxConcurrency;

	public AttendanceDispatcher(AttendanceProcessingService processingService, ProtocolClient protocolClient,
			@Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME) TaskExecutor taskExecutor,
			ProcessingProperties properties) {
		this.processingService = processingService;
		this.protocolClient = protocolClient;
		this.taskExecutor = taskExecutor;
		this.maxConcurrency = properties.maxConcurrency();
		this.slots = new Semaphore(maxConcurrency);
	}

	/**
	 * Quantos atendimentos estão com a chamada externa em andamento agora.
	 */
	public int inFlight() {
		return maxConcurrency - slots.availablePermits();
	}

	/**
	 * @return quantos atendimentos foram reservados nesta rodada
	 */
	public int dispatchDue() {
		int freeSlots = slots.drainPermits();
		List<Long> claimed;
		try {
			claimed = processingService.claimDue(freeSlots);
		}
		finally {
			// Se a reserva falhar, nada foi reservado e as vagas voltam todas.
			slots.release(freeSlots);
		}
		for (Long id : claimed) {
			submit(id);
		}
		return claimed.size();
	}

	private void submit(long id) {
		slots.acquireUninterruptibly();
		try {
			taskExecutor.execute(() -> processInSlot(id));
		}
		catch (RejectedExecutionException ex) {
			slots.release();
			log.warn("Atendimento {} não foi enviado ao processamento (aplicação encerrando); "
					+ "será retomado pela recuperação de abandonados", id);
		}
	}

	private void processInSlot(long id) {
		try {
			process(id);
		}
		catch (RuntimeException ex) {
			log.error("Falha ao registrar o resultado do atendimento {}; "
					+ "ele será retomado pela recuperação de abandonados", id, ex);
		}
		finally {
			slots.release();
		}
	}

	private void process(long id) {
		String protocol;
		try {
			protocol = protocolClient.fetchProtocol();
		}
		catch (ProtocolUnavailableException ex) {
			log.warn("Protocolo indisponível para o atendimento {}: {}", id, ex.getMessage());
			processingService.registerFailure(id, ex.getMessage());
			return;
		}
		processingService.complete(id, protocol);
	}

}
