package com.astro7.clinic.protocol;

import java.net.URI;
import java.util.UUID;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Obtém o protocolo do atendimento no HTTPBin ({@code GET /uuid}).
 * Toda forma de não receber um UUID vira {@link ProtocolUnavailableException}.
 */
@Component
public class ProtocolClient {

	private final RestClient restClient;
	private final URI url;

	public ProtocolClient(RestClient.Builder restClientBuilder, ProtocolProperties properties) {
		HttpClientSettings settings = HttpClientSettings.defaults()
				.withConnectTimeout(properties.connectTimeout())
				.withReadTimeout(properties.readTimeout());
		this.restClient = restClientBuilder
				.requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
				.build();
		this.url = properties.url();
	}

	public String fetchProtocol() {
		UuidResponse response;
		try {
			response = restClient.get().uri(url).retrieve().body(UuidResponse.class);
		}
		catch (RestClientException ex) {
			throw new ProtocolUnavailableException("Falha ao consultar " + url + ": " + ex.getMessage(), ex);
		}
		if (response == null || !isUuid(response.uuid())) {
			throw new ProtocolUnavailableException("Resposta de " + url + " sem UUID válido");
		}
		return response.uuid();
	}

	private static boolean isUuid(String value) {
		if (value == null) {
			return false;
		}
		try {
			return UUID.fromString(value).toString().equalsIgnoreCase(value);
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	record UuidResponse(String uuid) {
	}

}
