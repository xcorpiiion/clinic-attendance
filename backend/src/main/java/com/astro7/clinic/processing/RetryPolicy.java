package com.astro7.clinic.processing;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Espera exponencial: 5s, 10s, 20s... até o teto. Espaçar as tentativas evita
 * insistir num serviço que acabou de cair, que é justamente quando ele mais precisa
 * de folga para voltar.
 */
@Component
public class RetryPolicy {

	private static final int MAX_DOUBLINGS = 20;

	private final int maxAttempts;
	private final Duration initialBackoff;
	private final Duration maxBackoff;

	public RetryPolicy(ProcessingProperties properties) {
		this.maxAttempts = properties.maxAttempts();
		this.initialBackoff = properties.initialBackoff();
		this.maxBackoff = properties.maxBackoff();
	}

	/**
	 * @param attemptsMade tentativas já feitas, contando a que acabou de falhar
	 * @return quanto esperar pela próxima, ou vazio quando não há próxima
	 */
	public Optional<Duration> delayBeforeNextAttempt(int attemptsMade) {
		if (attemptsMade >= maxAttempts) {
			return Optional.empty();
		}
		int doublings = Math.clamp(attemptsMade - 1L, 0, MAX_DOUBLINGS);
		Duration delay = initialBackoff.multipliedBy(1L << doublings);
		return Optional.of(delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay);
	}

}
