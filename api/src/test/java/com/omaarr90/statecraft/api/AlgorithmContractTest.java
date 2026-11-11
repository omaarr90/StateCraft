package com.omaarr90.statecraft.api;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.api.ApiModels.Complex;
import com.omaarr90.statecraft.api.ApiModels.SimulateResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Mathematical contracts for the playground's bounded algorithm exercises.
 * These exercise the HTTP parser and the actual Java engine together. Simon and
 * the postselected order-finding state are teaching exercises, not claims of
 * full Simon or Shor implementations in the canonical algorithm suite.
 */
@QuarkusTest
class AlgorithmContractTest {

	private static final double EPS = 1e-10;
	private static final int SHOTS = 256;
	private static final List<String> QFT = List.of("h q[2];", "cp(pi/2) q[1], q[2];", "cp(pi/4) q[0], q[2];",
			"h q[1];", "cp(pi/2) q[0], q[1];", "h q[0];", "swap q[2], q[0];");
	private static final List<String> INVERSE_QFT = List.of("swap q[2], q[0];", "h q[0];", "cp(-pi/2) q[0], q[1];",
			"h q[1];", "cp(-pi/4) q[0], q[2];", "cp(-pi/2) q[1], q[2];", "h q[2];");

	@Test
	void groverAmplifiesMarked11IncludingItsGlobalPhase() {
		SimulateResponse result = simulate(
				qasm(2, List.of("h q[0];", "h q[1];", "cz q[0], q[1];", "h q[0];", "h q[1];", "x q[0];", "x q[1];",
						"cz q[0], q[1];", "x q[0];", "x q[1];", "h q[0];", "h q[1];")),
				Map.of("shots", SHOTS, "seed", 11));

		assertState(result.amplitudes(), realState(4, Map.of(3, -1.0)));
		assertEquals(Map.of("11", SHOTS), result.counts());
		assertProbabilities(result, 0, 0, 0, 1);
	}

	@Test
	void bernsteinVaziraniRecovers101OnOnlyTheInputRegister() {
		// The repository's four-bit 1011 example is reduced to three input bits
		// plus q3 as the phase-kickback ancilla, as in the website template.
		SimulateResponse result = simulate(
				qasm(4, List.of("x q[3];", "h q[0];", "h q[1];", "h q[2];", "h q[3];", "cx q[0], q[3];",
						"cx q[2], q[3];", "h q[0];", "h q[1];", "h q[2];")),
				Map.of("shots", SHOTS, "seed", 19, "measuredQubits", List.of(0, 1, 2)));

		double amplitude = 1 / Math.sqrt(2);
		assertState(result.amplitudes(), realState(16, Map.of(5, amplitude, 13, -amplitude)));
		assertEquals(Map.of("101", SHOTS), result.counts());
		assertArrayEquals(new int[]{0, 1, 2}, result.measuredQubits());
		assertEquals(0.5, result.probabilities()[5], EPS);
		assertEquals(0.5, result.probabilities()[13], EPS);
	}

	@Test
	void simonHidden11ProducesOnlyOrthogonalInputWitnesses() {
		SimulateResponse result = simulate(
				qasm(4, List.of("h q[0];", "h q[1];", "cx q[0], q[2];", "cx q[1], q[2];", "h q[0];", "h q[1];")),
				Map.of("shots", SHOTS, "seed", 23, "measuredQubits", List.of(0, 1)));

		assertState(result.amplitudes(), realState(16, Map.of(0, 0.5, 3, 0.5, 4, 0.5, 7, -0.5)));
		double[] inputMarginal = new double[4];
		for (int index = 0; index < result.probabilities().length; index++) {
			inputMarginal[index & 3] += result.probabilities()[index];
		}
		assertArrayEquals(new double[]{0.5, 0, 0, 0.5}, inputMarginal, EPS);
		assertTrue(Set.of("00", "11").containsAll(result.counts().keySet()));
		assertEquals(SHOTS, totalCounts(result));
		for (String label : result.counts().keySet()) {
			int witness = Integer.parseInt(label, 2);
			assertEquals(0, Integer.bitCount(witness & 0b11) % 2, "y dot s must be zero modulo two");
		}
	}

	@Test
	void qftMatchesAnalyticComplexPhaseAtEveryLittleEndianIndex() {
		List<String> circuit = new ArrayList<>(List.of("x q[0];", "x q[2];"));
		circuit.addAll(QFT);
		SimulateResponse result = simulate(qasm(3, circuit));

		// Final SWAP fixes bit reversal; this is the positive-sign Fourier
		// transform F|5>_y = exp(2*pi*i*5*y/8) / sqrt(8).
		List<Complex> expected = new ArrayList<>();
		for (int index = 0; index < 8; index++) {
			double phase = 2 * Math.PI * 5 * index / 8;
			expected.add(new Complex(Math.cos(phase) / Math.sqrt(8), Math.sin(phase) / Math.sqrt(8)));
		}
		assertState(result.amplitudes(), expected);
		assertProbabilities(result, 0.125, 0.125, 0.125, 0.125, 0.125, 0.125, 0.125, 0.125);
	}

	@Test
	void inverseQftReturnsThePreparedBasisStateWithoutChangingItsPhase() {
		List<String> circuit = new ArrayList<>(List.of("x q[0];", "x q[2];"));
		circuit.addAll(QFT);
		circuit.addAll(INVERSE_QFT);
		SimulateResponse result = simulate(qasm(3, circuit));

		assertState(result.amplitudes(), realState(8, Map.of(5, 1.0)));
		assertProbabilities(result, 0, 0, 0, 0, 0, 1, 0, 0);
	}

	@Test
	void reducedShorOrderFourStateYieldsEvenFourierOutcomes() {
		// For a=2, N=15, Q=8, postselecting residue 1 prepares
		// (|0> + |4>)/sqrt(2). This test supplies that state analytically;
		// it does not pretend to implement modular exponentiation or factoring.
		List<String> circuit = new ArrayList<>(List.of("h q[2];"));
		circuit.addAll(INVERSE_QFT);
		SimulateResponse result = simulate(qasm(3, circuit), Map.of("shots", SHOTS, "seed", 31));

		assertState(result.amplitudes(), realState(8, Map.of(0, 0.5, 2, 0.5, 4, 0.5, 6, 0.5)));
		assertProbabilities(result, 0.25, 0, 0.25, 0, 0.25, 0, 0.25, 0);
		assertTrue(Set.of("000", "010", "100", "110").containsAll(result.counts().keySet()));
		assertEquals(SHOTS, totalCounts(result));
	}

	@Test
	void subsetMeasurementCountsUsePackedSortedQubitsAndBinaryLabels() {
		// q2 q1 q0 = 011, but measured q2 q0 = 01. The unmeasured
		// q1 must not appear in the histogram label or its width.
		String source = qasm(3, List.of("x q[0];", "x q[1];"));
		SimulateResponse result = simulate(source, Map.of("shots", SHOTS, "seed", 37, "measuredQubits", List.of(2, 0)));

		assertState(result.amplitudes(), realState(8, Map.of(3, 1.0)));
		assertArrayEquals(new int[]{0, 2}, result.measuredQubits());
		assertEquals(Map.of("01", SHOTS), result.counts());
	}

	@Test
	void terminalQasmMeasurementsSelectTheCountRegisterWithoutCollapsingState() {
		String source = """
				OPENQASM 2.0;
				include "qelib1.inc";
				qreg q[3];
				creg c[2];
				x q[0];
				h q[1];
				measure q[0] -> c[0];
				measure q[2] -> c[1];
				""";
		SimulateResponse result = simulate(source, Map.of("shots", SHOTS, "seed", 41));

		double amplitude = 1 / Math.sqrt(2);
		assertState(result.amplitudes(), realState(8, Map.of(1, amplitude, 3, amplitude)));
		assertArrayEquals(new int[]{0, 2}, result.measuredQubits());
		assertEquals(Map.of("01", SHOTS), result.counts());
	}

	@Test
	void everyReturnedStepMatchesAnIndependentlyExecutedCircuitPrefix() {
		List<String> circuit = new ArrayList<>(List.of("x q[0];", "x q[2];"));
		circuit.addAll(QFT);
		SimulateResponse full = simulate(qasm(3, circuit));

		assertEquals(circuit.size(), full.operations().size());
		assertEquals(circuit.size() + 1, full.steps().size());
		assertState(full.steps().getFirst(), realState(8, Map.of(0, 1.0)));
		for (int prefixLength = 0; prefixLength <= circuit.size(); prefixLength++) {
			SimulateResponse prefix = simulate(qasm(3, circuit.subList(0, prefixLength)));
			assertState(full.steps().get(prefixLength), prefix.amplitudes());
		}
		assertState(full.steps().getLast(), full.amplitudes());
	}

	private static String qasm(int qubits, List<String> instructions) {
		return "OPENQASM 3.0;\nqubit[" + qubits + "] q;\n" + String.join("\n", instructions);
	}

	private static SimulateResponse simulate(String source) {
		return simulate(source, Map.of());
	}

	private static SimulateResponse simulate(String source, Map<String, Object> options) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("format", "qasm");
		request.put("source", source);
		request.put("includeSteps", true);
		request.putAll(options);
		SimulateResponse response = given().contentType(ContentType.JSON).body(request).when().post("/api/v1/simulate")
				.then().statusCode(200).extract().as(SimulateResponse.class);
		assertEquals("java", response.runtime());
		assertEquals("statevector", response.engine());
		assertEquals(1 << response.qubits(), response.amplitudes().size());
		return response;
	}

	private static List<Complex> realState(int size, Map<Integer, Double> nonzero) {
		List<Complex> amplitudes = new ArrayList<>();
		for (int index = 0; index < size; index++) {
			amplitudes.add(new Complex(nonzero.getOrDefault(index, 0.0), 0));
		}
		return amplitudes;
	}

	private static void assertState(List<Complex> actual, List<Complex> expected) {
		assertEquals(expected.size(), actual.size());
		for (int index = 0; index < expected.size(); index++) {
			assertEquals(expected.get(index).re(), actual.get(index).re(), EPS, "real amplitude at index " + index);
			assertEquals(expected.get(index).im(), actual.get(index).im(), EPS,
					"imaginary amplitude at index " + index);
		}
	}

	private static void assertProbabilities(SimulateResponse response, double... expected) {
		assertArrayEquals(expected, response.probabilities(), EPS);
	}

	private static int totalCounts(SimulateResponse response) {
		return response.counts().values().stream().mapToInt(Integer::intValue).sum();
	}
}
