package com.omaarr90.statecraft.engines.statevector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.MeasurementResult;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.noise.ErrorChannel;
import com.omaarr90.statecraft.core.noise.NoiseModel;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatevectorNoiseTest {

	private static final double EPS = 1e-12;

	@Test
	void amplitudeDampingNoiseSeedIsDeterministicAcrossRuns() {
		ErrorChannel channel = ErrorChannel.amplitudeDamping(0.4, 0);
		NoiseModel model = NoiseModel.builder().afterAllGates(channel).build();
		QuantumCircuit circuit = new QuantumCircuit(1).append(new PauliX(), 0);

		long seed = 123L;
		SimulationRequest request = SimulationRequest.zeroState(circuit).withNoiseModel(model).withNoiseSeed(seed);

		StatevectorEngine engine = new StatevectorEngine();
		StateVector first = engine.simulate(request).finalState().orElseThrow();
		StateVector second = engine.simulate(request).finalState().orElseThrow();

		assertArrayEquals(first.data(), second.data(), EPS);
	}

	@Test
	void phaseFlipOnPlusStateAppliesZ() {
		NoiseModel model = NoiseModel.builder().afterAllGates(ErrorChannel.phaseFlip(1.0, 0)).build();
		QuantumCircuit circuit = new QuantumCircuit(1).append(new Hadamard(), 0);

		StateVector finalState = new StatevectorEngine()
				.simulate(SimulationRequest.zeroState(circuit).withNoiseModel(model)).finalState().orElseThrow();

		double invSqrt2 = 1.0 / Math.sqrt(2.0);
		double[] expected = {invSqrt2, 0.0, -invSqrt2, 0.0};
		assertStateEquals(expected, finalState);
	}

	@Test
	void depolarizingNoiseSeedIsDeterministicAcrossRuns() {
		ErrorChannel channel = ErrorChannel.depolarizing(1.0, 0);
		NoiseModel model = NoiseModel.builder().afterAllGates(channel).build();
		QuantumCircuit circuit = new QuantumCircuit(1).append(new PauliX(), 0);

		long seed = 77L;
		SimulationRequest request = SimulationRequest.zeroState(circuit).withNoiseModel(model).withNoiseSeed(seed);

		StatevectorEngine engine = new StatevectorEngine();
		StateVector first = engine.simulate(request).finalState().orElseThrow();
		StateVector second = engine.simulate(request).finalState().orElseThrow();

		assertArrayEquals(first.data(), second.data(), EPS);
		assertNormalized(first);
	}

	@Test
	void phaseFlipNoiseSeedIsDeterministicAcrossRuns() {
		NoiseModel model = NoiseModel.builder().afterAllGates(ErrorChannel.phaseFlip(0.5, 0)).build();
		QuantumCircuit circuit = new QuantumCircuit(1).append(new Hadamard(), 0);
		long seed = 21L;
		SimulationRequest request = SimulationRequest.zeroState(circuit).withNoiseModel(model).withNoiseSeed(seed);

		StatevectorEngine engine = new StatevectorEngine();
		StateVector first = engine.simulate(request).finalState().orElseThrow();
		StateVector second = engine.simulate(request).finalState().orElseThrow();

		assertArrayEquals(first.data(), second.data(), EPS);
		assertNormalized(first);
	}

	@Test
	void amplitudeDampingDistributionMatchesProbability() {
		double gamma = 0.25;
		NoiseModel model = NoiseModel.builder().afterAllGates(ErrorChannel.amplitudeDamping(gamma, 0)).build();
		QuantumCircuit circuit = new QuantumCircuit(1).append(new PauliX(), 0);
		StatevectorEngine engine = new StatevectorEngine();

		int trials = 1000;
		int zeroCount = 0;
		for (int trial = 0; trial < trials; trial++) {
			SimulationRequest request = SimulationRequest.zeroState(circuit).withNoiseModel(model)
					.withNoiseSeed(0xBAD0L + trial);
			StateVector state = engine.simulate(request).finalState().orElseThrow();
			double p0 = (state.real(0) * state.real(0)) + (state.imag(0) * state.imag(0));
			if (p0 > 0.5) {
				zeroCount++;
			}
		}

		double observed = (double) zeroCount / trials;
		assertEquals(gamma, observed, 0.05);
	}

	@Test
	void invalidNoiseQubitThrows() {
		NoiseModel model = NoiseModel.builder().afterAllGates(ErrorChannel.phaseFlip(0.1, 2)).build();
		QuantumCircuit circuit = new QuantumCircuit(1).append(new Hadamard(), 0);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withNoiseModel(model);

		assertThrows(IllegalArgumentException.class, () -> new StatevectorEngine().simulate(request));
	}

	@Test
	void idleDecoherenceUsesMappedQubitInsteadOfChannelTemplate() {
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0);
		StateVector initialState = StateVector.fromArray(2, new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0});
		NoiseModel model = NoiseModel.builder().onQubits(ErrorChannel.amplitudeDamping(1.0, 0), 0, 1).build();
		SimulationRequest request = SimulationRequest.withInitialState(circuit, initialState).withNoiseModel(model);

		StateVector finalState = new StatevectorEngine().simulate(request).finalState().orElseThrow();
		double invSqrt2 = 1.0 / Math.sqrt(2.0);
		assertStateEquals(new double[]{invSqrt2, 0.0, -invSqrt2, 0.0, 0.0, 0.0, 0.0, 0.0}, finalState);
	}

	@Test
	void bellDemoAmplitudeDampingSeedThreeKeepsStateNormalized() {
		NoiseModel model = NoiseModel.builder().afterAllGates(ErrorChannel.amplitudeDamping(0.9, 0))
				.afterAllGates(ErrorChannel.amplitudeDamping(0.9, 1)).build();
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withNoiseModel(model).withNoiseSeed(3L);

		StateVector finalState = new StatevectorEngine().simulate(request).finalState().orElseThrow();

		assertNormalized(finalState);
	}

	@Test
	void amplitudeDampingOnGroundStateSkipsZeroBranch() {
		NoiseModel model = NoiseModel.builder().afterAllGates(ErrorChannel.amplitudeDamping(0.9, 1)).build();
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withNoiseModel(model).withNoiseSeed(3L);

		StateVector finalState = new StatevectorEngine().simulate(request).finalState().orElseThrow();
		double invSqrt2 = 1.0 / Math.sqrt(2.0);

		assertStateEquals(new double[]{invSqrt2, 0.0, invSqrt2, 0.0, 0.0, 0.0, 0.0, 0.0}, finalState);
		assertNormalized(finalState);
	}

	@Test
	void phaseDampingOnGroundStateSkipsZeroBranch() {
		NoiseModel model = NoiseModel.builder().afterAllGates(ErrorChannel.phaseDamping(0.9, 1)).build();
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withNoiseModel(model).withNoiseSeed(3L);

		StateVector finalState = new StatevectorEngine().simulate(request).finalState().orElseThrow();
		double invSqrt2 = 1.0 / Math.sqrt(2.0);

		assertStateEquals(new double[]{invSqrt2, 0.0, invSqrt2, 0.0, 0.0, 0.0, 0.0, 0.0}, finalState);
		assertNormalized(finalState);
	}

	@Test
	void scheduledNoisePathsMatchGoldenState() {
		NoiseModel model = NoiseModel.builder()
				.afterGate(QuantumCircuit.Operation.SingleGateOperation.class, ErrorChannel.phaseFlip(1.0, 0))
				.onQubits(ErrorChannel.amplitudeDamping(1.0, 0), 1).afterAllGates(ErrorChannel.phaseFlip(1.0, 0))
				.build();
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withBasisState(0, 1).withNoiseModel(model);

		StateVector finalState = new StatevectorEngine().simulate(request).finalState().orElseThrow();

		double invSqrt2 = 1.0 / Math.sqrt(2.0);
		assertStateEquals(new double[]{invSqrt2, 0.0, -invSqrt2, 0.0, 0.0, 0.0, 0.0, 0.0}, finalState);
	}

	@Test
	void noisyMeasurementPathMatchesGoldenHistogram() {
		NoiseModel model = NoiseModel.builder().afterAllGates(ErrorChannel.amplitudeDamping(1.0, 0)).build();
		QuantumCircuit circuit = new QuantumCircuit(1).append(new PauliX(), 0);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withNoiseModel(model)
				.withMeasurement(MeasurementInstruction.countsAll(16).withSeed(5L), false);

		MeasurementResult.Histogram histogram = (MeasurementResult.Histogram) new StatevectorEngine().simulate(request)
				.measurement().orElseThrow();

		assertEquals(16, histogram.shots());
		assertEquals(Map.of(0, 16), histogram.counts());
	}

	@Test
	void thermalRelaxationDrivesExcitedStateTowardGroundState() {
		NoiseModel model = NoiseModel.builder().afterAllGates(ErrorChannel.thermalRelaxation(1e-6, 2e-6, 1e-3, 0))
				.build();
		QuantumCircuit circuit = new QuantumCircuit(1).append(new PauliX(), 0);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withNoiseModel(model).withNoiseSeed(9L);

		StateVector finalState = new StatevectorEngine().simulate(request).finalState().orElseThrow();

		assertStateEquals(new double[]{1.0, 0.0, 0.0, 0.0}, finalState);
		assertNormalized(finalState);
	}

	private static void assertStateEquals(double[] expected, StateVector actual) {
		assertArrayEquals(expected, actual.data(), EPS);
	}

	private static void assertNormalized(StateVector state) {
		assertEquals(1.0, normSquared(state), EPS);
	}

	private static double normSquared(StateVector state) {
		double normSq = 0.0;
		for (int basis = 0; basis < state.dimension(); basis++) {
			double real = state.real(basis);
			double imag = state.imag(basis);
			normSq += (real * real) + (imag * imag);
		}
		return normSq;
	}
}
