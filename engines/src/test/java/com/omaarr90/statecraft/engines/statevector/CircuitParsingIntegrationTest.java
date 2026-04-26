package com.omaarr90.statecraft.engines.statevector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.core.parse.CircuitFormat;
import com.omaarr90.statecraft.core.parse.CircuitParsers;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CircuitParsingIntegrationTest {

	private static final double EPS = 1e-12;

	@Test
	void jsonParseThenSimulateMatchesCircuitApply(@TempDir Path tempDir) throws IOException {
		String json = """
				{
				  "qubits": 2,
				  "operations": [
				    { "gate": "h", "target": 0 },
				    { "gate": "cx", "control": 0, "target": 1 }
				  ]
				}
				""";

		Path input = tempDir.resolve("bell.json");
		Files.writeString(input, json);

		QuantumCircuit circuit = CircuitParsers.parse(input, CircuitFormat.AUTO);

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(SimulationRequest.zeroState(circuit));

		assertStateMatches(circuit.apply(), result.finalState().orElseThrow());
	}

	@Test
	void qasm2ParseThenSimulateMatchesEquivalentCircuit(@TempDir Path tempDir) throws IOException {
		String qasm = """
				OPENQASM 2.0;
				include "qelib1.inc";
				qreg q[2];

				h q[0];
				cu1(pi / 2) q[0], q[1];
				cx q[0], q[1];
				""";

		Path input = tempDir.resolve("common.qasm");
		Files.writeString(input, qasm);

		QuantumCircuit circuit = CircuitParsers.parse(input, CircuitFormat.AUTO);

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(SimulationRequest.zeroState(circuit));
		QuantumCircuit expected = new QuantumCircuit(2).append(new Hadamard(), 0)
				.appendControlledPhase(Math.PI / 2.0, 0, 1).append(CnotGate.of(), 0, 1);

		assertStateMatches(expected.apply(), result.finalState().orElseThrow());
	}

	@Test
	void qasmRotationParseThenSimulateMatchesExpectedState(@TempDir Path tempDir) throws IOException {
		String qasm = """
				OPENQASM 3.0;
				qubit[1] q;

				rx(pi) q[0];
				""";

		Path input = tempDir.resolve("rotation.qasm3");
		Files.writeString(input, qasm);

		QuantumCircuit circuit = CircuitParsers.parse(input, CircuitFormat.AUTO);

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(SimulationRequest.zeroState(circuit));
		StateVector state = result.finalState().orElseThrow();

		assertEquals(0.0, state.real(0), EPS);
		assertEquals(0.0, state.imag(0), EPS);
		assertEquals(0.0, state.real(1), EPS);
		assertEquals(-1.0, state.imag(1), EPS);
	}

	private static void assertStateMatches(ComplexNumber[] expected, StateVector actual) {
		assertEquals(expected.length, actual.dimension());
		for (int index = 0; index < expected.length; index++) {
			ComplexNumber exp = expected[index] == null ? ComplexNumber.zero() : expected[index];
			assertEquals(exp.real(), actual.real(index), EPS, "real mismatch at " + index);
			assertEquals(exp.imag(), actual.imag(index), EPS, "imag mismatch at " + index);
		}
	}
}
