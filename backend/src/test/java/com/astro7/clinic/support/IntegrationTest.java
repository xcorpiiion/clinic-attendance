package com.astro7.clinic.support;

import com.astro7.clinic.attendance.AttendanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.mysql.MySQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base dos testes contra MySQL de verdade: o SKIP LOCKED, a coluna gerada e o índice
 * único são do MySQL, e um banco em memória não provaria nenhum dos três.
 * <p>
 * Um container só para a suíte inteira, iniciado uma vez e encerrado pelo Testcontainers
 * no fim da JVM. O agendamento fica desligado: cada teste dispara as rodadas quando quer.
 */
@SpringBootTest(properties = "clinic.processing.enabled=false")
@AutoConfigureMockMvc
public abstract class IntegrationTest {

	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	static {
		MYSQL.start();
	}

	@Autowired
	protected AttendanceRepository attendanceRepository;

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
	}

	@Autowired
	private JdbcClient jdbcClient;

	@BeforeEach
	void cleanDatabase() {
		attendanceRepository.deleteAllInBatch();
	}

	/**
	 * Recua os horários no próprio banco, e não com um valor calculado aqui, para não
	 * depender de o fuso da JVM e o do MySQL coincidirem.
	 */
	protected void makeDueNow(long attendanceId) {
		jdbcClient.sql("UPDATE attendance SET next_attempt_at = next_attempt_at - INTERVAL 1 DAY WHERE id = ?")
				.param(attendanceId)
				.update();
	}

	protected void makeProcessingOld(long attendanceId) {
		jdbcClient.sql("UPDATE attendance SET updated_at = updated_at - INTERVAL 1 DAY WHERE id = ?")
				.param(attendanceId)
				.update();
	}

}
