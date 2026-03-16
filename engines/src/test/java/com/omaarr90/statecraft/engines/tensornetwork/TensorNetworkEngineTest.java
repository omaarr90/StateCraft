package com.omaarr90.statecraft.engines.tensornetwork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.MeasurementResult;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.core.noise.ErrorChannel;
import com.omaarr90.statecraft.core.noise.NoiseModel;
import com.omaarr90.statecraft.engines.statevector.StatevectorEngine;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class TensorNetworkEngineTest {

	private static final double EPS = 1e-12;

	@Test
	void idIsStable() {
		TensorNetworkEngine engine = new TensorNetworkEngine();
		assertEquals("tensornetwork", engine.id());
	}

	@Test
	void engineIsDiscoveredByServiceLoader() {
		List<String> ids = ServiceLoader.load(SimulatorEngine.class).stream().map(ServiceLoader.Provider::get)
				.map(SimulatorEngine::id).toList();
		assertTrue(ids.contains(TensorNetworkEngine.ID));
	}

	@Test
	void shallowCircuitMatchesStatevector() {
		QuantumCircuit circuit = new QuantumCircuit(4).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1)
				.append(new PauliX(), 3).appendSwap(1, 3).appendControlledPhase(Math.PI / 3.0, 0, 2)
				.append(CnotGate.of(), 2, 3);

		SimulationRequest request = SimulationRequest.zeroState(circuit);
		SimulationResult tensorResult = new TensorNetworkEngine().simulate(request);
		SimulationResult statevectorResult = new StatevectorEngine().simulate(request);

		assertStateEquals(statevectorResult.finalState().orElseThrow(), tensorResult.finalState().orElseThrow());
	}

	@Test
	void measurementSamplingWorks() {
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1);
		SimulationRequest request = SimulationRequest.zeroState(circuit)
				.withMeasurement(MeasurementInstruction.countsAll(1_500).withSeed(22L), false);

		SimulationResult result = new TensorNetworkEngine().simulate(request);
		MeasurementResult.Histogram histogram = (MeasurementResult.Histogram) result.measurement().orElseThrow();
		Map<Integer, Integer> counts = histogram.counts();

		assertTrue(result.finalState().isEmpty());
		assertEquals(1_500, histogram.shots());
		assertEquals(1_500, counts.values().stream().mapToInt(Integer::intValue).sum());
		assertTrue(counts.getOrDefault(0, 0) > 0);
		assertTrue(counts.getOrDefault(3, 0) > 0);
	}

	@Test
	void rejectsDeepCircuit() {
		QuantumCircuit circuit = new QuantumCircuit(1);
		for (int i = 0; i < TensorNetworkEngine.MAX_DEPTH + 1; i++) {
			circuit = circuit.append(new Hadamard(), 0);
		}
		SimulationRequest request = SimulationRequest.zeroState(circuit);

		assertThrows(UnsupportedOperationException.class, () -> new TensorNetworkEngine().simulate(request));
	}

	@Test
	void supportsCircuitAtDepthLimit() {
		QuantumCircuit circuit = new QuantumCircuit(1);
		for (int i = 0; i < TensorNetworkEngine.MAX_DEPTH; i++) {
			circuit = circuit.append(new Hadamard(), 0);
		}

		SimulationResult result = new TensorNetworkEngine().simulate(SimulationRequest.zeroState(circuit));
		StateVector state = result.finalState().orElseThrow();
		assertEquals(2, state.dimension());
	}

	@Test
	void rejectsNoisySimulation() {
		QuantumCircuit circuit = new QuantumCircuit(1).append(new Hadamard(), 0);
		NoiseModel noiseModel = NoiseModel.builder().afterAllGates(ErrorChannel.phaseFlip(0.1, 0)).build();
		SimulationRequest request = SimulationRequest.zeroState(circuit).withNoiseModel(noiseModel);

		assertThrows(UnsupportedOperationException.class, () -> new TensorNetworkEngine().simulate(request));
	}

	@Test
	void rejectsTooManyQubits() {
		QuantumCircuit circuit = new QuantumCircuit(TensorNetworkEngine.MAX_QUBITS + 1);
		SimulationRequest request = SimulationRequest.zeroState(circuit);

		assertThrows(UnsupportedOperationException.class, () -> new TensorNetworkEngine().simulate(request));
	}

	@Test
	void rejectsArbitraryTwoQubitUnitary() {
		ComplexNumber[] identity = new ComplexNumber[]{ComplexNumber.one(), ComplexNumber.zero(), ComplexNumber.zero(),
				ComplexNumber.zero(), ComplexNumber.zero(), ComplexNumber.one(), ComplexNumber.zero(),
				ComplexNumber.zero(), ComplexNumber.zero(), ComplexNumber.zero(), ComplexNumber.one(),
				ComplexNumber.zero(), ComplexNumber.zero(), ComplexNumber.zero(), ComplexNumber.zero(),
				ComplexNumber.one()};
		QuantumCircuit circuit = new QuantumCircuit(2).appendTwoQubitUnitary(identity, 0, 1);

		UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
				() -> new TensorNetworkEngine().simulate(SimulationRequest.zeroState(circuit)));
		assertTrue(exception.getMessage().contains("arbitrary two-qubit"));
	}

	@Test
	void rejectsMultiControlOperation() {
		QuantumCircuit circuit = new QuantumCircuit(3).appendMultiControl(new PauliX(), 2, 0, 1);

		UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
				() -> new TensorNetworkEngine().simulate(SimulationRequest.zeroState(circuit)));
		assertTrue(exception.getMessage().contains("multi-control"));
	}

	@Test
	void returnsBitstringHistogramForWideMeasurements() {
		QuantumCircuit circuit = new QuantumCircuit(40);
		SimulationRequest request = SimulationRequest.zeroState(circuit)
				.withMeasurement(MeasurementInstruction.countsAll(4).withSeed(7L), false);

		SimulationResult result = new TensorNetworkEngine().simulate(request);
		MeasurementResult.BitstringHistogram histogram = (MeasurementResult.BitstringHistogram) result.measurement()
				.orElseThrow();
		assertEquals(4, histogram.shots());
		assertEquals(1, histogram.counts().size());
		assertEquals(4, histogram.counts().getOrDefault("0".repeat(40), 0));
	}

	@Test
	void rejectsLargeFinalStateMaterialization() {
		QuantumCircuit circuit = new QuantumCircuit(TensorNetworkEngine.MAX_FINAL_STATE_QUBITS + 1);
		UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
				() -> new TensorNetworkEngine().simulate(SimulationRequest.zeroState(circuit)));
		assertTrue(exception.getMessage().contains("cannot materialize amplitudes"));
	}

	@Test
	void largeShallowCircuitSmokeRunsInShotsOnlyMode() {
		QuantumCircuit circuit = randomShallowCircuit(50, 40, 12345L);
		SimulationRequest request = SimulationRequest.zeroState(circuit)
				.withMeasurement(MeasurementInstruction.countsAll(4).withSeed(99L), false);

		SimulationResult result = new TensorNetworkEngine().simulate(request);
		MeasurementResult.BitstringHistogram histogram = (MeasurementResult.BitstringHistogram) result.measurement()
				.orElseThrow();
		assertTrue(result.finalState().isEmpty());
		assertEquals(4, histogram.shots());
		assertEquals(4, histogram.counts().values().stream().mapToInt(Integer::intValue).sum());
	}

	private static QuantumCircuit randomShallowCircuit(int qubits, int depth, long seed) {
		Random random = new Random(seed);
		QuantumCircuit circuit = new QuantumCircuit(qubits);
		for (int layer = 0; layer < depth; layer++) {
			int qubit = random.nextInt(qubits);
			circuit = random.nextBoolean()
					? circuit.append(new Hadamard(), qubit)
					: circuit.append(new PauliX(), qubit);
		}
		return circuit;
	}

	private static void assertStateEquals(StateVector expected, StateVector actual) {
		assertEquals(expected.dimension(), actual.dimension());
		for (int i = 0; i < expected.dimension(); i++) {
			assertEquals(expected.real(i), actual.real(i), EPS, "real mismatch at " + i);
			assertEquals(expected.imag(i), actual.imag(i), EPS, "imag mismatch at " + i);
		}
	}
}
