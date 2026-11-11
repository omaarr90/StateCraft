package com.omaarr90.statecraft.api;

import com.fasterxml.jackson.core.JsonParseException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(1)
public class JsonSyntaxExceptionMapper implements ExceptionMapper<JsonParseException> {
	@Override
	public Response toResponse(JsonParseException exception) {
		return ApiExceptionMapper.response(400, "INVALID_JSON", "Request must contain valid JSON.");
	}
}
