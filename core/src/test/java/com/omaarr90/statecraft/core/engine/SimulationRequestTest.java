package com.omaarr90.statecraft.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import org.junit.jupiter.api.Test;

class SimulationRequestTest {

    @Test
    void zeroStateFactoryWrapsCircuit() {
        QuantumCircuit circuit = new QuantumCircuit(2);
        SimulationRequest request = SimulationRequest.zeroState(circuit);
        assertEquals(circuit, request.circuit());
        assertTrue(request.initialState().isEmpty());
    }

    @Test
    void explicitStateMatchesCircuitQubitCount() {
        QuantumCircuit circuit = new QuantumCircuit(1);
        StateVector state = StateVector.zero(1);
        SimulationRequest request = SimulationRequest.withInitialState(circuit, state);
        assertEquals(state, request.initialState().orElseThrow());
    }

    @Test
    void mismatchedQubitCountThrows() {
        QuantumCircuit circuit = new QuantumCircuit(1);
        StateVector state = StateVector.zero(2);
        assertThrows(IllegalArgumentException.class,
                () -> SimulationRequest.withInitialState(circuit, state));
    }
}
