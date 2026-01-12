package com.omaarr90.statecraft.engines.statevector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.noise.ErrorChannel;
import com.omaarr90.statecraft.core.noise.KrausDecomposition;
import com.omaarr90.statecraft.core.noise.KrausOperator;
import com.omaarr90.statecraft.core.noise.NoiseModel;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class StatevectorNoiseTest {

    private static final double EPS = 1e-12;

    @Test
    void amplitudeDampingUsesNoiseSeed() {
        ErrorChannel channel = ErrorChannel.amplitudeDamping(0.4, 0);
        NoiseModel model = NoiseModel.builder()
                .afterAllGates(channel)
                .build();
        QuantumCircuit circuit = new QuantumCircuit(1).append(new PauliX(), 0);

        long seed = 123L;
        SimulationRequest request = SimulationRequest.zeroState(circuit)
                .withNoiseModel(model)
                .withNoiseSeed(seed);

        StatevectorEngine engine = new StatevectorEngine();
        SimulationResult result = engine.simulate(request);
        StateVector finalState = result.finalState().orElseThrow();

        KrausDecomposition decomposition = channel.krausDecomposition();
        int operatorIndex = decomposition.sampleOperator(new SplittableRandom(seed));
        KrausOperator operator = decomposition.operators().get(operatorIndex);
        double[] expected = applyOperator(new double[] {0.0, 0.0, 1.0, 0.0}, operator);

        assertStateEquals(expected, finalState);
    }

    @Test
    void phaseFlipOnPlusStateAppliesZ() {
        NoiseModel model = NoiseModel.builder()
                .afterAllGates(ErrorChannel.phaseFlip(1.0, 0))
                .build();
        QuantumCircuit circuit = new QuantumCircuit(1).append(new Hadamard(), 0);

        SimulationResult result = new StatevectorEngine().simulate(
                SimulationRequest.zeroState(circuit).withNoiseModel(model));
        StateVector finalState = result.finalState().orElseThrow();

        double invSqrt2 = 1.0 / Math.sqrt(2.0);
        double[] expected = {invSqrt2, 0.0, -invSqrt2, 0.0};
        assertStateEquals(expected, finalState);
    }

    @Test
    void depolarizingSampleMatchesExpectedOperator() {
        ErrorChannel channel = ErrorChannel.depolarizing(1.0, 0);
        NoiseModel model = NoiseModel.builder()
                .afterAllGates(channel)
                .build();
        QuantumCircuit circuit = new QuantumCircuit(1).append(new PauliX(), 0);

        long seed = 77L;
        SimulationResult result = new StatevectorEngine().simulate(
                SimulationRequest.zeroState(circuit)
                        .withNoiseModel(model)
                        .withNoiseSeed(seed));

        KrausDecomposition decomposition = channel.krausDecomposition();
        int operatorIndex = decomposition.sampleOperator(new SplittableRandom(seed));
        KrausOperator operator = decomposition.operators().get(operatorIndex);
        double[] expected = applyOperator(new double[] {0.0, 0.0, 1.0, 0.0}, operator);

        assertStateEquals(expected, result.finalState().orElseThrow());
    }

    @Test
    void amplitudeDampingDistributionMatchesProbability() {
        double gamma = 0.25;
        NoiseModel model = NoiseModel.builder()
                .afterAllGates(ErrorChannel.amplitudeDamping(gamma, 0))
                .build();
        QuantumCircuit circuit = new QuantumCircuit(1).append(new PauliX(), 0);
        StatevectorEngine engine = new StatevectorEngine();

        int trials = 1000;
        int zeroCount = 0;
        for (int trial = 0; trial < trials; trial++) {
            SimulationRequest request = SimulationRequest.zeroState(circuit)
                    .withNoiseModel(model)
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
        NoiseModel model = NoiseModel.builder()
                .afterAllGates(ErrorChannel.phaseFlip(0.1, 2))
                .build();
        QuantumCircuit circuit = new QuantumCircuit(1).append(new Hadamard(), 0);
        SimulationRequest request = SimulationRequest.zeroState(circuit).withNoiseModel(model);

        assertThrows(IllegalArgumentException.class, () -> new StatevectorEngine().simulate(request));
    }

    private static double[] applyOperator(double[] state, KrausOperator operator) {
        double a0r = state[0];
        double a0i = state[1];
        double a1r = state[2];
        double a1i = state[3];
        var matrix = operator.matrix();

        double m00r = matrix[0].real();
        double m00i = matrix[0].imag();
        double m01r = matrix[1].real();
        double m01i = matrix[1].imag();
        double m10r = matrix[2].real();
        double m10i = matrix[2].imag();
        double m11r = matrix[3].real();
        double m11i = matrix[3].imag();

        double new0r = m00r * a0r - m00i * a0i + m01r * a1r - m01i * a1i;
        double new0i = m00r * a0i + m00i * a0r + m01r * a1i + m01i * a1r;
        double new1r = m10r * a0r - m10i * a0i + m11r * a1r - m11i * a1i;
        double new1i = m10r * a0i + m10i * a0r + m11r * a1i + m11i * a1r;

        double normSq = (new0r * new0r) + (new0i * new0i) + (new1r * new1r) + (new1i * new1i);
        double scale = 1.0 / Math.sqrt(normSq);
        return new double[] {new0r * scale, new0i * scale, new1r * scale, new1i * scale};
    }

    private static void assertStateEquals(double[] expected, StateVector actual) {
        assertArrayEquals(expected, actual.data(), EPS);
    }
}
