package com.omaarr90.statecraft.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class SimulatorResourceTest {
	private static final String BELL = """
			{"qubits":2,"operations":[{"gate":"h","target":0},{"gate":"cx","control":0,"target":1}]}
			""";
	@Test
	void capabilitiesIdentifyTheRealEngineAndLimits() {
		given().get("/api/v1/capabilities").then().statusCode(200).body("engine", equalTo("statevector"), "runtime",
				equalTo("java"), "maxQubits", equalTo(6), "midCircuitMeasurement", equalTo(false), "noiseChannels",
				not(hasItem("bit-flip")));
	}
	@Test
	void bellStepsAndMeasurementUseTheCanonicalEngine() {
		given().contentType(ContentType.JSON)
				.body(Map.of("format", "json", "source", BELL, "includeSteps", true, "shots", 1000, "seed", 19))
				.post("/api/v1/simulate").then().statusCode(200).body("steps.size()", equalTo(3), "operations[1].gate",
						equalTo("CX"), "probabilities[0]", equalTo(0.5f), "probabilities[3]", equalTo(0.5f),
						"counts.01", nullValue(), "counts.10", nullValue());
	}
	@Test
	void littleEndianQubitZeroMapsToIndexOne() {
		given().contentType(ContentType.JSON)
				.body(Map.of("format", "qasm", "source", "OPENQASM 3.0; qubit[3] q; x q[0];")).post("/api/v1/simulate")
				.then().statusCode(200).body("probabilities[1]", equalTo(1f), "probabilities[4]", equalTo(0f));
	}
	@Test
	void rejectsLargeRegistersBeforeCanonicalParserExpansion() {
		for (String source : List.of("OPENQASM 3.0; qubit[1000000000] q; measure q;",
				"OPENQASM 2.0; qreg q[2147483647]; measure q;")) {
			given().contentType(ContentType.JSON).body(Map.of("format", "qasm", "source", source)).post("/api/v1/parse")
					.then().statusCode(400).body("error.code", equalTo("INVALID_REQUEST"));
		}
		given().contentType(ContentType.JSON).body(Map.of("format", "json", "source", "{\"qubits\":4294967297}"))
				.post("/api/v1/simulate").then().statusCode(400);
	}
	@Test
	void rejectsInvalidStateAndShotsAndSafeIntegerSeeds() {
		Map<String, Object> base = Map.of("format", "json", "source", BELL);
		for (Map<String, Object> extra : List.of(Map.<String, Object>of("shots", 10001),
				Map.<String, Object>of("seed", 9007199254740992L),
				Map.<String, Object>of("initialState", List.of(Map.of("re", 0, "im", 0))))) {
			var request = new java.util.HashMap<>(base);
			request.putAll(extra);
			given().contentType(ContentType.JSON).body(request).post("/api/v1/simulate").then().statusCode(400)
					.body("error.code", equalTo("INVALID_REQUEST"));
		}
	}
	@Test
	void fractionalSeedsAreRejectedInsteadOfTruncated() {
		given().contentType(ContentType.JSON)
				.body("{\"format\":\"json\",\"source\":\"{\\\"qubits\\\":1}\",\"seed\":1.5}").post("/api/v1/simulate")
				.then().statusCode(400);
	}
	@Test
	void collapseReportsAnExplicitExtensionAndNormalizesOneBranch() {
		given().contentType(ContentType.JSON)
				.body(Map.of("qubits", 2, "target", 0, "seed", 1, "amplitudes",
						List.of(Map.of("re", Math.sqrt(.5), "im", 0), Map.of("re", 0, "im", 0),
								Map.of("re", 0, "im", 0), Map.of("re", Math.sqrt(.5), "im", 0))))
				.post("/api/v1/collapse").then().statusCode(200).body("engine", equalTo("statevector"), "extension",
						equalTo("api-collapse"), "probability", equalTo(0.5f));
	}
	@Test
	void amplitudeDampingUsesActualCoreKrausChannel() {
		given().contentType(ContentType.JSON)
				.body(Map.of("qubits", 1, "target", 0, "seed", 1, "channel", "amplitude-damping", "strength", 1,
						"amplitudes", List.of(Map.of("re", 0, "im", 0), Map.of("re", 1, "im", 0))))
				.post("/api/v1/noise").then().statusCode(200)
				.body("probabilities[0]", equalTo(1f), "probabilities[1]", equalTo(0f));
	}
	@Test
	void noisyPrefixReplayMatchesTheFinalSeededTrajectory() {
		var response = given().contentType(ContentType.JSON)
				.body(Map.of("format", "json", "source", BELL, "includeSteps", true, "noise",
						Map.of("channel", "depolarizing", "strength", .8, "seed", 7)))
				.post("/api/v1/simulate").then().statusCode(200).extract().as(ApiModels.SimulateResponse.class);
		org.junit.jupiter.api.Assertions.assertEquals(response.amplitudes(), response.steps().getLast());
	}
	@Test
	void rejectsUnsupportedAdapterGateAndMidCircuitMeasurement() {
		for (String source : List.of("OPENQASM 3.0; qubit[1] q; u(0,0,0) q[0];",
				"OPENQASM 3.0; qubit[1] q; measure q[0]; x q[0];")) {
			given().contentType(ContentType.JSON).body(Map.of("format", "qasm", "source", source))
					.post("/api/v1/simulate").then().statusCode(400).body("error.code", equalTo("INVALID_REQUEST"));
		}
	}
	@Test
	void invalidRequestJsonUsesSanitizedErrorShape() {
		given().contentType(ContentType.JSON).body("{broken").post("/api/v1/simulate").then().statusCode(400)
				.body("error.code", equalTo("INVALID_JSON"));
		given().contentType(ContentType.JSON).body("{\"seed\":1.5}").post("/api/v1/simulate").then().statusCode(400)
				.body("error.code", equalTo("INVALID_JSON"));
	}

}
