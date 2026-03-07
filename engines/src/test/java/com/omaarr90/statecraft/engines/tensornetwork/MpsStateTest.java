package com.omaarr90.statecraft.engines.tensornetwork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.engines.statevector.StatevectorEngine;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MpsStateTest {

    private static final double EPS = 1e-9;

    @Test
    void bellStateUpdateStaysNormalized() {
        MpsState state = MpsState.zeroState(2, new EjmlComplexSvdAdapter(), 256, 1e-12);
        state.applySingleGate(0, new Hadamard());
        state.applyCnot(0, 1);

        StateVector vector = state.toStateVectorLogicalOrder();
        assertEquals(1.0, normSquared(vector), EPS);
        assertEquals(Math.sqrt(0.5), Math.abs(vector.real(0)), EPS);
        assertEquals(Math.sqrt(0.5), Math.abs(vector.real(3)), EPS);
    }

    @Test
    void nonAdjacentRoutingMatchesStatevector() {
        QuantumCircuit circuit = new QuantumCircuit(4)
                .append(new Hadamard(), 0)
                .append(CnotGate.of(), 0, 3)
                .append(CnotGate.of(), 3, 1)
                .appendSwap(0, 2);

        MpsState state = MpsState.zeroState(4, new EjmlComplexSvdAdapter(), 256, 1e-12);
        state.applySingleGate(0, new Hadamard());
        state.applyCnot(0, 3);
        state.applyCnot(3, 1);
        state.applySwapGate(0, 2);
        assertEquals(0.0, state.totalDiscardedWeight(), 1e-9);

        StateVector expected = new StatevectorEngine()
                .simulate(SimulationRequest.zeroState(circuit))
                .finalState()
                .orElseThrow();
        StateVector actual = state.toStateVectorLogicalOrder();

        for (int index = 0; index < expected.dimension(); index++) {
            assertEquals(expected.real(index), actual.real(index), EPS, "real mismatch at " + index);
            assertEquals(expected.imag(index), actual.imag(index), EPS, "imag mismatch at " + index);
        }
    }

    @Test
    void truncationBookkeepingTracksDiscardedWeight() {
        StateVector random = randomNormalizedState(6, 0xC0FFEEL);
        MpsState state = MpsState.fromStateVector(random, new EjmlComplexSvdAdapter(), 1, 1e-12);

        assertTrue(state.totalDiscardedWeight() > 0.0);
        assertEquals(1, state.maxObservedBondDimension());
    }

    private static double normSquared(StateVector vector) {
        double norm = 0.0;
        for (int index = 0; index < vector.dimension(); index++) {
            double real = vector.real(index);
            double imag = vector.imag(index);
            norm += (real * real) + (imag * imag);
        }
        return norm;
    }

    private static StateVector randomNormalizedState(int qubits, long seed) {
        int dimension = 1 << qubits;
        Random random = new Random(seed);
        double[] data = new double[dimension << 1];
        double normSq = 0.0;
        for (int index = 0; index < dimension; index++) {
            double real = random.nextDouble() - 0.5;
            double imag = random.nextDouble() - 0.5;
            data[index << 1] = real;
            data[(index << 1) + 1] = imag;
            normSq += (real * real) + (imag * imag);
        }
        double scale = 1.0 / Math.sqrt(normSq);
        for (int index = 0; index < data.length; index++) {
            data[index] *= scale;
        }
        return StateVector.fromArray(qubits, data);
    }
}
