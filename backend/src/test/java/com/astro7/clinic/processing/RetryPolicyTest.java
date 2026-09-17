package com.astro7.clinic.processing;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

	private final RetryPolicy policy = new RetryPolicy(new ProcessingProperties(
			10, 5, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2)));

	@Test
	void doublesTheDelayAfterEachFailure() {
		assertThat(policy.delayBeforeNextAttempt(1)).contains(Duration.ofSeconds(5));
		assertThat(policy.delayBeforeNextAttempt(2)).contains(Duration.ofSeconds(10));
		assertThat(policy.delayBeforeNextAttempt(3)).contains(Duration.ofSeconds(20));
	}

	@Test
	void capsTheDelay() {
		assertThat(policy.delayBeforeNextAttempt(4)).contains(Duration.ofSeconds(30));
	}

	@Test
	void stopsWhenAttemptsRunOut() {
		assertThat(policy.delayBeforeNextAttempt(5)).isEmpty();
		assertThat(policy.delayBeforeNextAttempt(6)).isEmpty();
	}

	@Test
	void doesNotOverflowWithLargeLimits() {
		RetryPolicy generous = new RetryPolicy(new ProcessingProperties(
				10, 1_000, Duration.ofSeconds(5), Duration.ofHours(1), Duration.ofMinutes(2)));

		assertThat(generous.delayBeforeNextAttempt(999)).contains(Duration.ofHours(1));
	}

}
