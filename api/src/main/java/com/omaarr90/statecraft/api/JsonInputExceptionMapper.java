package com.omaarr90.statecraft.api;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(1)
public class JsonInputExceptionMapper implements ExceptionMapper<MismatchedInputException> {
	@Override
	public Response toResponse(MismatchedInputException exception) {
		return ApiExceptionMapper.response(400, "INVALID_JSON",
				"Request must contain valid JSON with the documented field types.");
	}
}
