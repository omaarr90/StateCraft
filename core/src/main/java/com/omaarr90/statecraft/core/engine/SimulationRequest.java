package com.omaarr90.statecraft.core.engine;

import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.Objects;
import java.util.Optional;

/**
 * Request payload for running a simulator engine against a quantum circuit.
 * Encapsulates the circuit and an optional initial state vector; if no state is provided,
 * the engine must assume the |0...0⟩ basis state.
 */
public record SimulationRequest(QuantumCircuit circuit, Optional<StateVector> initialState) {

    public SimulationRequest {
        Objects.requireNonNull(circuit, "circuit");
        initialState = initialState == null ? Optional.empty() : initialState;
        initialState.ifPresent(state -> {
            Objects.requireNonNull(state, "initialState");
            if (state.qubitCount() != circuit.qubitCount()) {
                throw new IllegalArgumentException(
                        "Initial state qubit count " + state.qubitCount()
                                + " does not match circuit qubit count " + circuit.qubitCount());
            }
        });
    }

    public static SimulationRequest zeroState(QuantumCircuit circuit) {
        return new SimulationRequest(circuit, Optional.empty());
    }

    public static SimulationRequest withInitialState(QuantumCircuit circuit, StateVector state) {
        Objects.requireNonNull(state, "state");
        return new SimulationRequest(circuit, Optional.of(state));
    }
}
