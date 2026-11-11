package com.omaarr90.statecraft.api;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Optional service token belongs on the server, never in browser source. */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiAuthenticationFilter implements ContainerRequestFilter {
	@ConfigProperty(name = "statecraft.api-token", defaultValue = "")
	Optional<String> configuredToken;
	@Override
	public void filter(ContainerRequestContext request) {
		String token = configuredToken.orElse("");
		if (token == null || token.isBlank()
				|| !request.getUriInfo().getPath().replaceFirst("^/", "").startsWith("api/v1")
				|| "OPTIONS".equals(request.getMethod()))
			return;
		String authorization = request.getHeaderString("Authorization");
		byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
		byte[] actual = authorization == null ? new byte[0] : authorization.getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(expected, actual)) {
			request.abortWith(
					ApiExceptionMapper.response(401, "UNAUTHORIZED", "A valid simulator service token is required."));
		}
	}
}
