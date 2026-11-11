package com.omaarr90.statecraft.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {
	private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);
	@Override
	public Response toResponse(Exception exception) {
		if (exception instanceof ApiException error) {
			return response(error.status, error.code, error.getMessage());
		}
		if (exception instanceof IllegalArgumentException || exception instanceof UnsupportedOperationException) {
			return response(400, "INVALID_REQUEST", safeMessage(exception));
		}
		if (exception instanceof JsonProcessingException) {
			return response(400, "INVALID_JSON", "Request must contain valid JSON with the documented field types.");
		}
		if (exception instanceof WebApplicationException web) {
			int status = web.getResponse().getStatus();
			return response(status, status == 400 ? "INVALID_JSON" : "HTTP_" + status,
					status == 400
							? "Request must contain valid JSON with the documented field types."
							: "Request could not be completed.");
		}
		LOG.error("Simulator request failed", exception);
		return response(500, "SIMULATION_FAILED", "The simulator could not complete this request.");
	}
	private static String safeMessage(Exception exception) {
		String message = exception.getMessage();
		return message == null ? "Invalid simulator request." : message.substring(0, Math.min(400, message.length()));
	}
	static Response response(int status, String code, String message) {
		return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
				.entity(new ApiModels.ErrorResponse(new ApiModels.ErrorDetail(code, message))).build();
	}
}
