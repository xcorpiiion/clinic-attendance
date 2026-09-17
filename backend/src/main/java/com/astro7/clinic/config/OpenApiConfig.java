package com.astro7.clinic.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	@Bean
	OpenAPI clinicOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Clinic Attendance API")
				.description("Abertura de atendimentos e acompanhamento do protocolo.")
				.version("v1"));
	}

}
