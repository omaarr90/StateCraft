package com.omaarr90.statecraft.engines.statevector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.MeasurementResult;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.PauliY;
import com.omaarr90.statecraft.quantum.PauliZ;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.SingleQubitGate;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class StatevectorEngineTest {

	private static final double EPS = 1e-12;
	private static final long RANDOM_SEED = 0x51D5L;

	@Test
	void idIsStable() {
		StatevectorEngine engine = new StatevectorEngine();
		assertEquals("statevector", engine.id());
	}

	@Test
	void simulateFromZeroStateMatchesCircuitApply() {
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1);

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(SimulationRequest.zeroState(circuit));

		ComplexNumber[] expected = circuit.apply();
		assertStateMatches(expected, result.finalState().orElseThrow());
	}

	@Test
	void simulateFromCustomStateMatchesCircuitApply() {
		QuantumCircuit circuit = new QuantumCircuit(1).append(new Hadamard(), 0);

		double[] data = {0.0, 0.0, 1.0, 0.0}; // |1>
		StateVector initial = StateVector.fromArray(1, data);

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(SimulationRequest.withInitialState(circuit, initial));

		ComplexNumber[] expected = circuit.apply(new ComplexNumber[]{ComplexNumber.zero(), ComplexNumber.one()});
		assertStateMatches(expected, result.finalState().orElseThrow());
	}

	@Test
	void simulateFromBasisStateMatchesCircuitApply() {
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 1).append(new PauliZ(), 0);

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(SimulationRequest.zeroState(circuit).withBasisState(0));

		ComplexNumber[] expected = circuit.apply(new ComplexNumber[]{ComplexNumber.zero(), ComplexNumber.one(),
				ComplexNumber.zero(), ComplexNumber.zero()});
		assertStateMatches(expected, result.finalState().orElseThrow());
	}

	@Test
	void simulateIdentityCircuitPreservesInitialStateAmplitudes() {
		int qubits = 3;
		QuantumCircuit circuit = new QuantumCircuit(qubits);

		SplittableRandom rng = new SplittableRandom(RANDOM_SEED ^ 0x5151L);
		int dimension = 1 << qubits;
		double[] data = new double[dimension << 1];
		double normSq = 0.0;
		for (int index = 0; index < dimension; index++) {
			int base = index << 1;
			double real = rng.nextDouble() - 0.5;
			double imag = rng.nextDouble() - 0.5;
			data[base] = real;
			data[base + 1] = imag;
			normSq += (real * real) + (imag * imag);
		}
		double scale = 1.0 / Math.sqrt(normSq);
		for (int i = 0; i < data.length; i++) {
			data[i] *= scale;
		}
		StateVector initial = StateVector.fromArray(qubits, data);
		double[] expected = initial.copyData();

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(SimulationRequest.withInitialState(circuit, initial));

		StateVector finalState = result.finalState().orElseThrow();
		assertArrayEquals(expected, finalState.data(), EPS);
	}

	@Test
	void simulateMultiQubitCircuitMatchesReference() {
		int qubits = 5;
		QuantumCircuit circuit = new QuantumCircuit(qubits).append(new Hadamard(), 4).append(new Hadamard(), 1)
				.append(new PauliX(), 0).append(CnotGate.of(), 4, 2).append(new Hadamard(), 2);

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(SimulationRequest.zeroState(circuit));

		ComplexNumber[] expected = circuit.apply();
		assertStateMatches(expected, result.finalState().orElseThrow());
	}

	@Test
	void nullRequestThrows() {
		SimulatorEngine engine = new StatevectorEngine();
		assertThrows(NullPointerException.class, () -> engine.simulate(null));
	}

	@Test
	void simulateTwoQubitUnitaryMatchesReference() {
		Random random = new Random(RANDOM_SEED);
		for (int trial = 0; trial < 6; trial++) {
			QuantumCircuit source = randomTwoQubitCircuit(random, 6);
			ComplexNumber[] matrix = extractUnitaryMatrix(source);
			QuantumCircuit circuit = new QuantumCircuit(2).appendTwoQubitUnitary(matrix, 0, 1);
			assertSimulationMatchesCircuit(circuit);
		}
	}

	@Test
	void simulateTwoQubitUnitaryOnNonAdjacentQubitsMatchesReference() {
		Random random = new Random(RANDOM_SEED ^ 0x8FF0L);
		ComplexNumber[] matrix = extractUnitaryMatrix(randomTwoQubitCircuit(random, 5));

		QuantumCircuit circuit = new QuantumCircuit(3);
		circuit = circuit.append(new Hadamard(), 1);
		circuit = circuit.appendTwoQubitUnitary(matrix, 2, 0);
		circuit = circuit.append(new PauliZ(), 1);
		assertSimulationMatchesCircuit(circuit);
	}

	@Test
	void simulateControlledPhaseMatchesReference() {
		double angle = Math.PI / 3.0;

		QuantumCircuit controlLower = new QuantumCircuit(2).append(new Hadamard(), 0).append(new Hadamard(), 1)
				.appendControlledPhase(angle, 0, 1);
		assertSimulationMatchesCircuit(controlLower);

		QuantumCircuit controlHigher = new QuantumCircuit(2).append(new Hadamard(), 0).append(new Hadamard(), 1)
				.appendControlledPhase(angle, 1, 0);
		assertSimulationMatchesCircuit(controlHigher);
	}

	@Test
	void simulateSwapMatchesReference() {
		QuantumCircuit circuit = new QuantumCircuit(3);
		circuit = circuit.append(new Hadamard(), 0);
		circuit = circuit.append(new PauliX(), 2);
		circuit = circuit.appendSwap(0, 2);
		circuit = circuit.append(new Hadamard(), 1);
		assertSimulationMatchesCircuit(circuit);
	}

	@Test
	void simulateMultiControlMatchesReference() {
		QuantumCircuit circuit = new QuantumCircuit(3);
		circuit = circuit.append(new Hadamard(), 0);
		circuit = circuit.append(new Hadamard(), 1);
		circuit = circuit.appendMultiControl(new PauliX(), 2, 0, 1);
		assertSimulationMatchesCircuit(circuit);
	}

	@Test
	void simulateMultiControlWithThreeControlsMatchesReference() {
		QuantumCircuit circuit = new QuantumCircuit(4);
		circuit = circuit.append(new Hadamard(), 0);
		circuit = circuit.append(new Hadamard(), 1);
		circuit = circuit.append(new Hadamard(), 2);
		circuit = circuit.appendMultiControl(new PauliX(), 3, 0, 1, 2);
		assertSimulationMatchesCircuit(circuit);
	}

	@Test
	void simulateGhZCircuitMatchesReference() {
		QuantumCircuit circuit = new QuantumCircuit(3);
		circuit = circuit.append(new Hadamard(), 0);
		circuit = circuit.append(CnotGate.of(), 0, 1);
		circuit = circuit.appendMultiControl(new PauliX(), 2, 0, 1);
		assertSimulationMatchesCircuit(circuit);
	}

	@Test
	void simulateQftThreeQubitsMatchesReference() {
		QuantumCircuit qft = buildQftCircuit(3);
		Random random = new Random(RANDOM_SEED ^ 0xABCDL);
		ComplexNumber[] initial = randomNormalizedState(random, 1 << 3);
		StateVector initialState = toStateVector(3, initial);

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(SimulationRequest.withInitialState(qft, initialState));

		ComplexNumber[] expected = qft.apply(initial);
		assertStateMatches(expected, result.finalState().orElseThrow());
	}

	@Test
	void bellStateHistogramShowsBalancedCounts() {
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1);

		MeasurementInstruction instruction = MeasurementInstruction.countsAll(10_000).withSeed(0xCAFEBABEL);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(instruction);

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(request);

		MeasurementResult measurement = result.measurement().orElseThrow();
		MeasurementResult.Histogram histogram = (MeasurementResult.Histogram) measurement;
		assertEquals(10_000, histogram.shots());
		assertArrayEquals(new int[]{0, 1}, histogram.measuredQubits());
		Map<Integer, Integer> counts = histogram.counts();
		int count00 = counts.getOrDefault(0, 0);
		int count11 = counts.getOrDefault(3, 0);
		assertEquals(10_000, count00 + count11);
		double expected = 5000.0;
		double tolerance = 400.0;
		assertEquals(expected, count00, tolerance);
		assertEquals(expected, count11, tolerance);
	}

	@Test
	void partialMeasurementTargetsSpecifiedQubits() {
		QuantumCircuit circuit = new QuantumCircuit(3).append(new Hadamard(), 0).append(new Hadamard(), 1)
				.append(CnotGate.of(), 0, 2);

		MeasurementInstruction instruction = MeasurementInstruction.counts(5_000, 2).withSeed(0xDEADL);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(instruction);

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(request);

		MeasurementResult.Histogram histogram = (MeasurementResult.Histogram) result.measurement().orElseThrow();
		assertArrayEquals(new int[]{2}, histogram.measuredQubits());
		Map<Integer, Integer> counts = histogram.counts();
		assertEquals(2, counts.size());
		int zeros = counts.getOrDefault(0, 0);
		int ones = counts.getOrDefault(1, 0);
		assertEquals(5_000, zeros + ones);
		assertTrue(zeros > 0);
		assertTrue(ones > 0);
	}

	@Test
	void measurementOperationsDefineDefaultTargets() {
		QuantumCircuit circuit = new QuantumCircuit(3).append(new Hadamard(), 0).append(new PauliX(), 2).measure(0, 2);

		MeasurementInstruction instruction = MeasurementInstruction.countsAll(1_000).withSeed(42L);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(instruction);

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(request);

		MeasurementResult.Histogram histogram = (MeasurementResult.Histogram) result.measurement().orElseThrow();
		assertArrayEquals(new int[]{0, 2}, histogram.measuredQubits());
		Map<Integer, Integer> counts = histogram.counts();
		assertEquals(1_000, counts.values().stream().mapToInt(Integer::intValue).sum());
	}

	@Test
	void shotSamplingSupportsRawOutcomes() {
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1);

		MeasurementInstruction instruction = MeasurementInstruction.samplesAll(16).withSeed(123L);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(instruction);

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(request);

		MeasurementResult.Samples samples = (MeasurementResult.Samples) result.measurement().orElseThrow();
		assertEquals(16, samples.shots());
		for (int outcome : samples.outcomes()) {
			assertTrue(outcome == 0 || outcome == 3);
		}
	}

	@Test
	void unitaryAfterMeasurementThrows() {
		QuantumCircuit circuit = new QuantumCircuit(1).append(new Hadamard(), 0).measure(0).append(new PauliX(), 0);

		MeasurementInstruction instruction = MeasurementInstruction.countsAll(32);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(instruction);

		StatevectorEngine engine = new StatevectorEngine();
		assertThrows(UnsupportedOperationException.class, () -> engine.simulate(request));
	}

	@Test
	void measurementSeedIsDeterministic() {
		QuantumCircuit circuit = new QuantumCircuit(3).append(new Hadamard(), 0).append(new Hadamard(), 1)
				.append(CnotGate.of(), 1, 2);
		MeasurementInstruction instruction = MeasurementInstruction.countsAll(2_048).withSeed(0xD00DL);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(instruction, false);

		StatevectorEngine engine = new StatevectorEngine();
		MeasurementResult.Histogram first = (MeasurementResult.Histogram) engine.simulate(request).measurement()
				.orElseThrow();
		MeasurementResult.Histogram second = (MeasurementResult.Histogram) engine.simulate(request).measurement()
				.orElseThrow();

		assertArrayEquals(first.measuredQubits(), second.measuredQubits());
		assertEquals(first.shots(), second.shots());
		assertEquals(first.counts(), second.counts());
	}

	@Test
	void largeCircuitNearTargetLimitRemainsNumericallySane() {
		int qubits = 22;
		QuantumCircuit circuit = new QuantumCircuit(qubits).append(new PauliX(), 0).append(new PauliX(), 5)
				.append(new PauliX(), 21).append(CnotGate.of(), 21, 1).append(CnotGate.of(), 0, 2).appendSwap(5, 3);

		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(SimulationRequest.zeroState(circuit));
		StateVector state = result.finalState().orElseThrow();

		int expectedIndex = (1 << 0) | (1 << 1) | (1 << 2) | (1 << 3) | (1 << 21);
		assertEquals(1.0, state.real(expectedIndex), EPS);
		assertEquals(0.0, state.imag(expectedIndex), EPS);
		assertNormalized(state);
	}

	@Test
	void rejectsQubitCountAboveDenseLimit() {
		QuantumCircuit circuit = new QuantumCircuit(30);
		SimulationRequest request = SimulationRequest.zeroState(circuit);

		StatevectorEngine engine = new StatevectorEngine();
		UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
				() -> engine.simulate(request));

		assertTrue(exception.getMessage().contains("supports up to 29 qubits"));
	}

	private void assertSimulationMatchesCircuit(QuantumCircuit circuit) {
		StatevectorEngine engine = new StatevectorEngine();
		SimulationResult result = engine.simulate(SimulationRequest.zeroState(circuit));
		ComplexNumber[] expected = circuit.apply();
		assertStateMatches(expected, result.finalState().orElseThrow());
	}

	private void assertStateMatches(ComplexNumber[] expected, StateVector actual) {
		for (int index = 0; index < expected.length; index++) {
			ComplexNumber amp = expected[index] == null ? ComplexNumber.zero() : expected[index];
			assertEquals(amp.real(), actual.real(index), EPS, "real mismatch at " + index);
			assertEquals(amp.imag(), actual.imag(index), EPS, "imag mismatch at " + index);
		}
	}

	private void assertNormalized(StateVector state) {
		double normSq = 0.0;
		for (int index = 0; index < state.dimension(); index++) {
			double real = state.real(index);
			double imag = state.imag(index);
			normSq += (real * real) + (imag * imag);
		}
		assertEquals(1.0, normSq, 1e-10, "state norm drifted");
	}

	private QuantumCircuit randomTwoQubitCircuit(Random random, int depth) {
		QuantumCircuit circuit = new QuantumCircuit(2);
		for (int i = 0; i < depth; i++) {
			if (random.nextBoolean()) {
				circuit = circuit.append(randomSingleGate(random), random.nextInt(2));
			} else {
				int control = random.nextInt(2);
				int target = 1 - control;
				circuit = circuit.append(CnotGate.of(), control, target);
			}
		}
		return circuit;
	}

	private SingleQubitGate randomSingleGate(Random random) {
		return switch (random.nextInt(4)) {
			case 0 -> new PauliX();
			case 1 -> new PauliY();
			case 2 -> new PauliZ();
			default -> new Hadamard();
		};
	}

	private ComplexNumber[] extractUnitaryMatrix(QuantumCircuit circuit) {
		int dimension = 1 << circuit.qubitCount();
		ComplexNumber[] matrix = new ComplexNumber[dimension * dimension];
		for (int col = 0; col < dimension; col++) {
			ComplexNumber[] basis = new ComplexNumber[dimension];
			basis[col] = ComplexNumber.one();
			ComplexNumber[] transformed = circuit.apply(basis);
			for (int row = 0; row < dimension; row++) {
				matrix[row * dimension + col] = transformed[row];
			}
		}
		return matrix;
	}

	private ComplexNumber[] randomNormalizedState(Random random, int dimension) {
		ComplexNumber[] state = new ComplexNumber[dimension];
		double normSquared = 0.0;
		for (int index = 0; index < dimension; index++) {
			double real = random.nextDouble() - 0.5;
			double imag = random.nextDouble() - 0.5;
			state[index] = new ComplexNumber(real, imag);
			normSquared += state[index].magnitudeSquared();
		}
		double scale = 1.0 / Math.sqrt(normSquared);
		for (int index = 0; index < dimension; index++) {
			state[index] = state[index].scale(scale);
		}
		return state;
	}

	private StateVector toStateVector(int qubitCount, ComplexNumber[] amplitudes) {
		int dimension = amplitudes.length;
		double[] data = new double[dimension << 1];
		for (int index = 0; index < dimension; index++) {
			ComplexNumber amp = amplitudes[index];
			data[index << 1] = amp.real();
			data[(index << 1) + 1] = amp.imag();
		}
		return StateVector.fromArray(qubitCount, data);
	}

	private QuantumCircuit buildQftCircuit(int qubits) {
		QuantumCircuit circuit = new QuantumCircuit(qubits);
		for (int target = 0; target < qubits; target++) {
			circuit = circuit.append(new Hadamard(), target);
			for (int control = target + 1; control < qubits; control++) {
				double angle = Math.PI / (1 << (control - target));
				circuit = circuit.appendControlledPhase(angle, control, target);
			}
		}
		for (int i = 0; i < qubits / 2; i++) {
			circuit = circuit.appendSwap(i, qubits - i - 1);
		}
		return circuit;
	}
}
