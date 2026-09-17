package com.astro7.clinic.processing;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param maxConcurrency  quantos atendimentos podem estar consultando o serviço externo ao mesmo tempo
 * @param maxAttempts     tentativas antes de o atendimento ir para FAILED
 * @param initialBackoff  espera antes da segunda tentativa; dobra a cada falha
 * @param maxBackoff      teto da espera entre tentativas
 * @param staleAfter      tempo em PROCESSING a partir do qual o atendimento é tido como abandonado
 *                        (o serviço caiu no meio da chamada). Precisa ser bem maior que o timeout
 *                        da chamada externa, senão um atendimento ainda em andamento seria retomado
 */
@Validated
@ConfigurationProperties("clinic.processing")
public record ProcessingProperties(
		@Positive int maxConcurrency,
		@Positive int maxAttempts,
		@NotNull Duration initialBackoff,
		@NotNull Duration maxBackoff,
		@NotNull Duration staleAfter) {
}
