package com.bouba.backend_trans.exception;

import java.time.LocalDateTime;
import java.util.Map;

public class ErrorResponse {

	private final LocalDateTime timestamp = LocalDateTime.now();
	private final int status;
	private final String message;
	private final Map<String, String> errors;

	public ErrorResponse(int status, String message) {
		this(status, message, null);
	}

	public ErrorResponse(int status, String message, Map<String, String> errors) {
		this.status = status;
		this.message = message;
		this.errors = errors;
	}

	public LocalDateTime getTimestamp() {
		return timestamp;
	}

	public int getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}

	public Map<String, String> getErrors() {
		return errors;
	}
}
