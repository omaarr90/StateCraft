package com.omaarr90.statecraft.engines.stabilizer;

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
import com.omaarr90.statecraft.core.noise.ErrorChannel;
import com.omaarr90.statecraft.core.noise.NoiseModel;
import com.omaarr90.statecraft.engines.statevector.StatevectorEngine;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.PauliY;
import com.omaarr90.statecraft.quantum.PauliZ;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.SGate;
import com.omaarr90.statecraft.quantum.SdgGate;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class StabilizerEngineTest {

	private static final double EPS = 1e-12;

	@Test
	void idIsStable() {
		StabilizerEngine engine = new StabilizerEngine();
		assertEquals("stabilizer", engine.id());
	}

	@Test
	void engineIsDiscoveredByServiceLoader() {
		List<String> ids = ServiceLoader.load(SimulatorEngine.class).stream().map(ServiceLoader.Provider::get)
				.map(SimulatorEngine::id).toList();
		assertTrue(ids.contains(StabilizerEngine.ID));
	}

	@Test
	void cliffordCircuitMatchesStatevectorResult() {
		QuantumCircuit circuit = new QuantumCircuit(3).append(new Hadamard(), 0).append(new SGate(), 1)
				.append(CnotGate.of(), 0, 1).appendMultiControl(new PauliY(), 2, 1).append(new SdgGate(), 2)
				.appendSwap(0, 2).appendControlledZ(2, 1);
		SimulationRequest request = SimulationRequest.zeroState(circuit);

		SimulationResult stabilizerResult = new StabilizerEngine().simulate(request);
		SimulationResult statevectorResult = new StatevectorEngine().simulate(request);

		assertStateEquals(statevectorResult.finalState().orElseThrow(), stabilizerResult.finalState().orElseThrow());
	}

	@Test
	void basisStateInitializationMatchesStatevector() {
		QuantumCircuit circuit = new QuantumCircuit(3).append(new SGate(), 0).append(new Hadamard(), 1)
				.append(CnotGate.of(), 1, 2).append(new SdgGate(), 2);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withBasisState(0, 2);

		SimulationResult stabilizerResult = new StabilizerEngine().simulate(request);
		SimulationResult statevectorResult = new StatevectorEngine().simulate(request);

		assertStateEquals(statevectorResult.finalState().orElseThrow(), stabilizerResult.finalState().orElseThrow());
	}

	@Test
	void measurementSamplingWorks() {
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1);
		MeasurementInstruction instruction = MeasurementInstruction.countsAll(2_000).withSeed(7L);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(instruction, false);

		SimulationResult result = new StabilizerEngine().simulate(request);
		MeasurementResult.Histogram histogram = (MeasurementResult.Histogram) result.measurement().orElseThrow();
		Map<Integer, Integer> counts = histogram.counts();

		assertTrue(result.finalState().isEmpty());
		assertEquals(2_000, histogram.shots());
		assertEquals(2_000, counts.values().stream().mapToInt(Integer::intValue).sum());
		assertTrue(counts.getOrDefault(0, 0) > 0);
		assertTrue(counts.getOrDefault(3, 0) > 0);
	}

	@Test
	void measurementTargetsDefaultToCircuitSuffix() {
		QuantumCircuit circuit = new QuantumCircuit(3).append(new PauliX(), 2).measure(0, 2);
		SimulationRequest request = SimulationRequest.zeroState(circuit)
				.withMeasurement(MeasurementInstruction.countsAll(32).withSeed(2L), false);

		MeasurementResult.Histogram histogram = (MeasurementResult.Histogram) new StabilizerEngine().simulate(request)
				.measurement().orElseThrow();

		assertArrayEquals(new int[]{0, 2}, histogram.measuredQubits());
		assertEquals(Map.of(0b10, 32), histogram.counts());
	}

	@Test
	void largeMeasurementOnlyRequestsReturnBitstringResults() {
		QuantumCircuit circuit = new QuantumCircuit(40);
		SimulationRequest request = SimulationRequest.zeroState(circuit)
				.withMeasurement(MeasurementInstruction.countsAll(8).withSeed(11L), false);

		MeasurementResult.BitstringHistogram histogram = (MeasurementResult.BitstringHistogram) new StabilizerEngine()
				.simulate(request).measurement().orElseThrow();

		assertEquals(8, histogram.shots());
		assertEquals(Map.of("0000000000000000000000000000000000000000", 8), histogram.counts());
	}

	@Test
	void rejectsGenericControlledPhaseGate() {
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0).appendControlledPhase(Math.PI / 2.0, 0,
				1);
		SimulationRequest request = SimulationRequest.zeroState(circuit);

		assertThrows(UnsupportedOperationException.class, () -> new StabilizerEngine().simulate(request));
	}

	@Test
	void supportsControlledZDiagonalGate() {
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0).append(new Hadamard(), 1)
				.appendControlledPhase(Math.PI, 0, 1);
		SimulationRequest request = SimulationRequest.zeroState(circuit);

		SimulationResult stabilizerResult = new StabilizerEngine().simulate(request);
		SimulationResult statevectorResult = new StatevectorEngine().simulate(request);

		assertStateEquals(statevectorResult.finalState().orElseThrow(), stabilizerResult.finalState().orElseThrow());
	}

	@Test
	void rejectsMultiControlNonPauliGate() {
		QuantumCircuit circuit = new QuantumCircuit(2).appendMultiControl(new Hadamard(), 1, 0);
		SimulationRequest request = SimulationRequest.zeroState(circuit);

		assertThrows(UnsupportedOperationException.class, () -> new StabilizerEngine().simulate(request));
	}

	@Test
	void rejectsMultiControlWithMoreThanOneControl() {
		QuantumCircuit circuit = new QuantumCircuit(3).appendMultiControl(new PauliX(), 2, 0, 1);
		SimulationRequest request = SimulationRequest.zeroState(circuit);

		assertThrows(UnsupportedOperationException.class, () -> new StabilizerEngine().simulate(request));
	}

	@Test
	void rejectsArbitraryTwoQubitUnitary() {
		ComplexNumber[] identity = new ComplexNumber[]{ComplexNumber.one(), ComplexNumber.zero(), ComplexNumber.zero(),
				ComplexNumber.zero(), ComplexNumber.zero(), ComplexNumber.one(), ComplexNumber.zero(),
				ComplexNumber.zero(), ComplexNumber.zero(), ComplexNumber.zero(), ComplexNumber.one(),
				ComplexNumber.zero(), ComplexNumber.zero(), ComplexNumber.zero(), ComplexNumber.zero(),
				ComplexNumber.one()};
		QuantumCircuit circuit = new QuantumCircuit(2).appendTwoQubitUnitary(identity, 0, 1);
		SimulationRequest request = SimulationRequest.zeroState(circuit);

		assertThrows(UnsupportedOperationException.class, () -> new StabilizerEngine().simulate(request));
	}

	@Test
	void rejectsNoiseModels() {
		QuantumCircuit circuit = new QuantumCircuit(1).append(new Hadamard(), 0);
		NoiseModel noise = NoiseModel.builder().afterAllGates(ErrorChannel.phaseFlip(0.1, 0)).build();
		SimulationRequest request = SimulationRequest.zeroState(circuit).withNoiseModel(noise);

		assertThrows(UnsupportedOperationException.class, () -> new StabilizerEngine().simulate(request));
	}

	@Test
	void rejectsNonBasisInitialState() {
		StateVector initial = StateVector.fromArray(1,
				new double[]{1.0 / Math.sqrt(2.0), 0.0, 1.0 / Math.sqrt(2.0), 0.0});
		SimulationRequest request = SimulationRequest.withInitialState(new QuantumCircuit(1), initial);

		assertThrows(UnsupportedOperationException.class, () -> new StabilizerEngine().simulate(request));
	}

	@Test
	void rejectsLargeFinalStateRequests() {
		QuantumCircuit circuit = new QuantumCircuit(StabilizerEngine.MAX_FINAL_STATE_QUBITS + 1);
		SimulationRequest request = SimulationRequest.zeroState(circuit);

		assertThrows(UnsupportedOperationException.class, () -> new StabilizerEngine().simulate(request));
	}

	private static void assertStateEquals(StateVector expected, StateVector actual) {
		assertEquals(expected.dimension(), actual.dimension());
		for (int index = 0; index < expected.dimension(); index++) {
			assertEquals(expected.real(index), actual.real(index), EPS, "real mismatch at " + index);
			assertEquals(expected.imag(index), actual.imag(index), EPS, "imag mismatch at " + index);
		}
	}
}
