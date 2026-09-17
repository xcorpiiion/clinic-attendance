package com.astro7.clinic.web;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("clinic.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
