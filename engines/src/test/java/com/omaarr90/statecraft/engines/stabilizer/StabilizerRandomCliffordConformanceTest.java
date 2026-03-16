package com.omaarr90.statecraft.engines.stabilizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.MeasurementResult;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.PauliY;
import com.omaarr90.statecraft.quantum.PauliZ;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.SGate;
import com.omaarr90.statecraft.quantum.SdgGate;
import com.omaarr90.statecraft.quantum.StateVector;
import com.omaarr90.statecraft.engines.statevector.StatevectorEngine;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StabilizerRandomCliffordConformanceTest {

	private static final double STATE_EPS = 1e-12;
	private static final int SHOTS = 4_096;
	private static final double DISTRIBUTION_TOL = 0.05;

	@Test
	void randomCliffordCircuitsMatchStatevector() {
		Random random = new Random(0x5A17B1L);
		StabilizerEngine stabilizer = new StabilizerEngine();
		StatevectorEngine statevector = new StatevectorEngine();

		for (int trial = 0; trial < 24; trial++) {
			int qubits = 1 + random.nextInt(6);
			int depth = 4 + random.nextInt(16);
			QuantumCircuit circuit = randomCliffordCircuit(random, qubits, depth);

			StateVector expected = statevector.simulate(SimulationRequest.zeroState(circuit)).finalState()
					.orElseThrow();
			StateVector actual = stabilizer.simulate(SimulationRequest.zeroState(circuit)).finalState().orElseThrow();
			assertStateClose(expected, actual);

			MeasurementInstruction instruction = MeasurementInstruction.countsAll(SHOTS).withSeed(0xC0FFEE + trial);
			SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(instruction, false);

			MeasurementResult.Histogram expectedHistogram = (MeasurementResult.Histogram) statevector.simulate(request)
					.measurement().orElseThrow();
			MeasurementResult.Histogram actualHistogram = (MeasurementResult.Histogram) stabilizer.simulate(request)
					.measurement().orElseThrow();
			assertHistogramClose(expectedHistogram, actualHistogram);
		}
	}

	private static QuantumCircuit randomCliffordCircuit(Random random, int qubits, int depth) {
		QuantumCircuit circuit = new QuantumCircuit(qubits);
		for (int step = 0; step < depth; step++) {
			int choice = random.nextInt(qubits == 1 ? 6 : 10);
			switch (choice) {
				case 0 -> circuit = circuit.append(new Hadamard(), random.nextInt(qubits));
				case 1 -> circuit = circuit.append(new SGate(), random.nextInt(qubits));
				case 2 -> circuit = circuit.append(new SdgGate(), random.nextInt(qubits));
				case 3 -> circuit = circuit.append(new PauliX(), random.nextInt(qubits));
				case 4 -> circuit = circuit.append(new PauliY(), random.nextInt(qubits));
				case 5 -> circuit = circuit.append(new PauliZ(), random.nextInt(qubits));
				case 6 -> {
					int[] pair = randomDistinctPair(random, qubits);
					circuit = circuit.appendControlledX(pair[0], pair[1]);
				}
				case 7 -> {
					int[] pair = randomDistinctPair(random, qubits);
					circuit = circuit.appendControlledY(pair[0], pair[1]);
				}
				case 8 -> {
					int[] pair = randomDistinctPair(random, qubits);
					circuit = circuit.appendControlledZ(pair[0], pair[1]);
				}
				case 9 -> {
					int[] pair = randomDistinctPair(random, qubits);
					circuit = circuit.appendSwap(pair[0], pair[1]);
				}
				default -> throw new IllegalStateException("unexpected choice: " + choice);
			}
		}
		return circuit;
	}

	private static int[] randomDistinctPair(Random random, int qubits) {
		int first = random.nextInt(qubits);
		int second = random.nextInt(qubits - 1);
		if (second >= first) {
			second++;
		}
		return new int[]{first, second};
	}

	private static void assertStateClose(StateVector expected, StateVector actual) {
		assertEquals(expected.dimension(), actual.dimension());
		for (int index = 0; index < expected.dimension(); index++) {
			assertEquals(expected.real(index), actual.real(index), STATE_EPS, "real mismatch at " + index);
			assertEquals(expected.imag(index), actual.imag(index), STATE_EPS, "imag mismatch at " + index);
		}
	}

	private static void assertHistogramClose(MeasurementResult.Histogram expected, MeasurementResult.Histogram actual) {
		assertArrayEquals(expected.measuredQubits(), actual.measuredQubits());
		assertEquals(expected.shots(), actual.shots());

		int measuredBits = expected.measuredQubits().length;
		int outcomes = 1 << measuredBits;
		Map<Integer, Integer> expectedCounts = expected.counts();
		Map<Integer, Integer> actualCounts = actual.counts();
		double shots = expected.shots();

		for (int outcome = 0; outcome < outcomes; outcome++) {
			double pExpected = expectedCounts.getOrDefault(outcome, 0) / shots;
			double pActual = actualCounts.getOrDefault(outcome, 0) / shots;
			assertEquals(pExpected, pActual, DISTRIBUTION_TOL, "distribution mismatch at " + outcome);
		}
	}
}
