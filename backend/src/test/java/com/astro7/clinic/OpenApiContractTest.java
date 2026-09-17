package com.astro7.clinic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.astro7.clinic.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O front gera os tipos TypeScript a partir de {@code frontend/api/openapi.json}.
 * Este teste falha quando a API muda e o arquivo não acompanha, para o contrato
 * não divergir em silêncio.
 * <p>
 * Para atualizar: {@code ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true},
 * e depois {@code npm run gen:api} no front.
 */
class OpenApiContractTest extends IntegrationTest {

	private static final Path CONTRACT = Path.of("..", "frontend", "api", "openapi.json");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void publishedContractMatchesTheApi() throws Exception {
		String body = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode current = withoutServers(jsonMapper.readTree(body));

		if (Boolean.getBoolean("openapi.update")) {
			write(current);
		}

		assertThat(CONTRACT).as("contrato publicado para o front").exists();
		JsonNode published = jsonMapper.readTree(Files.readString(CONTRACT));
		assertThat(published)
				.as("frontend/api/openapi.json está desatualizado; rode com -Dopenapi.update=true")
				.isEqualTo(current);
	}

	/** A URL do servidor muda a cada execução e não faz parte do contrato. */
	private static JsonNode withoutServers(JsonNode document) {
		((ObjectNode) document).remove("servers");
		return document;
	}

	private void write(JsonNode document) throws IOException {
		Files.createDirectories(CONTRACT.getParent());
		Files.writeString(CONTRACT, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n");
	}

}
