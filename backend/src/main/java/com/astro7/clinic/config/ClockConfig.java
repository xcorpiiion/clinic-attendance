package com.astro7.clinic.config;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

	/**
	 * Em segundos porque as colunas são DATETIME sem fração: o MySQL arredondaria
	 * a fração, e um horário gravado poderia ficar um segundo à frente do lido.
	 */
	@Bean
	Clock clock() {
		return Clock.tickSeconds(ZoneOffset.UTC);
	}

}
