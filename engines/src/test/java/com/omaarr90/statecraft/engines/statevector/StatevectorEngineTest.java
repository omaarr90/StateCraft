package com.omaarr90.statecraft.engines.statevector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import org.junit.jupiter.api.Test;

class StatevectorEngineTest {

    private static final double EPS = 1e-12;

    @Test
    void idIsStable() {
        StatevectorEngine engine = new StatevectorEngine();
        assertEquals("statevector", engine.id());
    }

    @Test
    void simulateFromZeroStateMatchesCircuitApply() {
        QuantumCircuit circuit = new QuantumCircuit(2)
                .append(new Hadamard(), 0)
                .append(CnotGate.of(), 0, 1);

        StatevectorEngine engine = new StatevectorEngine();
        SimulationResult result = engine.simulate(SimulationRequest.zeroState(circuit));

        ComplexNumber[] expected = circuit.apply();
        assertStateMatches(expected, result.finalState());
    }

    @Test
    void simulateFromCustomStateMatchesCircuitApply() {
        QuantumCircuit circuit = new QuantumCircuit(1).append(new Hadamard(), 0);

        double[] data = {0.0, 0.0, 1.0, 0.0}; // |1>
        StateVector initial = StateVector.fromArray(1, data);

        StatevectorEngine engine = new StatevectorEngine();
        SimulationResult result = engine.simulate(
                SimulationRequest.withInitialState(circuit, initial));

        ComplexNumber[] expected = circuit.apply(new ComplexNumber[] {
                ComplexNumber.zero(), ComplexNumber.one()
        });
        assertStateMatches(expected, result.finalState());
    }

    @Test
    void nullRequestThrows() {
        StatevectorEngine engine = new StatevectorEngine();
        assertThrows(NullPointerException.class, () -> engine.simulate(null));
    }

    private void assertStateMatches(ComplexNumber[] expected, StateVector actual) {
        for (int index = 0; index < expected.length; index++) {
            ComplexNumber amp = expected[index] == null ? ComplexNumber.zero() : expected[index];
            assertEquals(amp.real(), actual.real(index), EPS, "real mismatch at " + index);
            assertEquals(amp.imag(), actual.imag(index), EPS, "imag mismatch at " + index);
        }
    }
}
