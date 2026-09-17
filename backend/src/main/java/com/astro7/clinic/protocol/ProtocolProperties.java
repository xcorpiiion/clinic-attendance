package com.astro7.clinic.protocol;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * A URL é completa, e não só a base, para dar para apontar para os endpoints do
 * HTTPBin que simulam falha ({@code /status/500}) e atraso ({@code /delay/10})
 * sem mexer no código.
 */
@Validated
@ConfigurationProperties("clinic.protocol")
public record ProtocolProperties(
		@NotNull URI url,
		@NotNull Duration connectTimeout,
		@NotNull Duration readTimeout) {
}
