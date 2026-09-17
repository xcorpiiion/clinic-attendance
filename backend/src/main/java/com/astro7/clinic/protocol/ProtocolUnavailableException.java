package com.astro7.clinic.protocol;

/**
 * O serviço externo não entregou um protocolo: estava fora, demorou além do
 * limite ou respondeu algo que não é um UUID. Vale uma nova tentativa.
 */
public class ProtocolUnavailableException extends RuntimeException {

	public ProtocolUnavailableException(String message) {
		super(message);
	}

	public ProtocolUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
