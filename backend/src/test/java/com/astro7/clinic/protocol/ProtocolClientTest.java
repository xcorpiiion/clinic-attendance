package com.astro7.clinic.protocol;

import java.net.URI;
import java.time.Duration;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ProtocolClientTest {

	private static final String PROTOCOL = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

	@RegisterExtension
	static final WireMockExtension HTTPBIN = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	private ProtocolClient client;

	@BeforeEach
	void createClient() {
		ProtocolProperties properties = new ProtocolProperties(
				URI.create(HTTPBIN.baseUrl() + "/uuid"), Duration.ofSeconds(1), Duration.ofMillis(500));
		client = new ProtocolClient(RestClient.builder(), properties);
	}

	@Test
	void returnsTheUuidAsProtocol() {
		HTTPBIN.stubFor(get("/uuid").willReturn(okJson("{\"uuid\": \"" + PROTOCOL + "\"}")));

		assertThat(client.fetchProtocol()).isEqualTo(PROTOCOL);
	}

	@Test
	void failsOnServerError() {
		HTTPBIN.stubFor(get("/uuid").willReturn(serviceUnavailable()));

		assertThatExceptionOfType(ProtocolUnavailableException.class)
				.isThrownBy(client::fetchProtocol)
				.withMessageContaining("503");
	}

	@Test
	void failsWhenResponseTakesLongerThanTheReadTimeout() {
		HTTPBIN.stubFor(get("/uuid").willReturn(okJson("{\"uuid\": \"" + PROTOCOL + "\"}").withFixedDelay(2_000)));

		long startedAt = System.nanoTime();
		assertThatExceptionOfType(ProtocolUnavailableException.class).isThrownBy(client::fetchProtocol);
		assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofMillis(1_500));
	}

	@Test
	void failsWhenConnectionDrops() {
		HTTPBIN.stubFor(get("/uuid").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		assertThatExceptionOfType(ProtocolUnavailableException.class).isThrownBy(client::fetchProtocol);
	}

	@Test
	void failsWhenUuidIsMissing() {
		HTTPBIN.stubFor(get("/uuid").willReturn(okJson("{\"origin\": \"10.0.0.1\"}")));

		assertThatExceptionOfType(ProtocolUnavailableException.class)
				.isThrownBy(client::fetchProtocol)
				.withMessageContaining("sem UUID");
	}

	@Test
	void failsWhenUuidIsMalformed() {
		HTTPBIN.stubFor(get("/uuid").willReturn(okJson("{\"uuid\": \"not-a-uuid\"}")));

		assertThatExceptionOfType(ProtocolUnavailableException.class).isThrownBy(client::fetchProtocol);
	}

	@Test
	void failsWhenBodyIsNotJson() {
		HTTPBIN.stubFor(get("/uuid").willReturn(aResponse()
				.withHeader("Content-Type", "text/html")
				.withBody("<html>Bad Gateway</html>")));

		assertThatExceptionOfType(ProtocolUnavailableException.class).isThrownBy(client::fetchProtocol);
	}

	@Test
	void failsWhenBodyIsEmpty() {
		HTTPBIN.stubFor(get("/uuid").willReturn(aResponse().withStatus(200)));

		assertThatExceptionOfType(ProtocolUnavailableException.class).isThrownBy(client::fetchProtocol);
	}

}
