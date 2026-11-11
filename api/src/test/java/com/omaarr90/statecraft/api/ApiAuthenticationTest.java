package com.omaarr90.statecraft.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(ApiAuthenticationTest.TokenProfile.class)
class ApiAuthenticationTest {
	public static class TokenProfile implements QuarkusTestProfile {
		@Override
		public Map<String, String> getConfigOverrides() {
			return Map.of("statecraft.api-token", "test-service-token");
		}
	}
	@Test
	void serviceTokenIsRequiredWhenConfigured() {
		given().get("/api/v1/capabilities").then().statusCode(401).body("error.code", equalTo("UNAUTHORIZED"));
		given().header("Authorization", "Bearer incorrect").get("/api/v1/capabilities").then().statusCode(401);
		given().header("Authorization", "Bearer test-service-token").get("/api/v1/capabilities").then().statusCode(200)
				.body("runtime", equalTo("java"));
	}
	@Test
	void healthRemainsAvailableForContainerReadiness() {
		given().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
	}
}
