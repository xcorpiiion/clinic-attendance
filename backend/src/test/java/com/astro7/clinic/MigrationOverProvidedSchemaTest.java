package com.astro7.clinic;

import java.nio.file.Path;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O banco do {@code database/docker-compose.yml} nasce com a tabela pelo
 * {@code init.sql}. As migrações precisam partir dali sem recriá-la.
 * Os outros testes cobrem o caminho do banco vazio, em que a V1 roda.
 */
@Testcontainers
class MigrationOverProvidedSchemaTest {

	private static final Path PROVIDED_INIT_SCRIPT = Path.of("..", "database", "init.sql");

	@Container
	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
			.withCopyFileToContainer(MountableFile.forHostPath(PROVIDED_INIT_SCRIPT),
					"/docker-entrypoint-initdb.d/init.sql");

	@Test
	void appliesOnlyTheNewMigrationOverTheProvidedTable() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());

		MigrateResult result = Flyway.configure()
				.dataSource(dataSource)
				.baselineOnMigrate(true)
				.baselineVersion("1")
				.load()
				.migrate();

		assertThat(result.migrations).extracting(migration -> migration.version).containsExactly("2");
		assertThat(JdbcClient.create(dataSource)
				.sql("""
						SELECT COUNT(*) FROM information_schema.columns
						WHERE table_schema = DATABASE() AND table_name = 'attendance'
						AND column_name IN ('attempts', 'next_attempt_at', 'last_error', 'version', 'open_cpf')
						""")
				.query(Integer.class)
				.single()).isEqualTo(5);
	}

}
