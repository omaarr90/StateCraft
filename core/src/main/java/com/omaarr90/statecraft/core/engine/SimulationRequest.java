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
public record SimulationRequest(
        QuantumCircuit circuit,
        Optional<StateVector> initialState,
        Optional<MeasurementInstruction> measurement,
        boolean returnFinalState) {

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
        measurement = measurement == null ? Optional.empty() : measurement;
        measurement.ifPresent(instruction -> {
            Objects.requireNonNull(instruction, "measurement");
            instruction.measuredQubits().ifPresent(qubits -> {
                if (qubits.length == 0) {
                    throw new IllegalArgumentException("measurement qubit list must not be empty");
                }
                for (int qubit : qubits) {
                    if (qubit < 0 || qubit >= circuit.qubitCount()) {
                        throw new IllegalArgumentException(
                                "measurement qubit out of range: " + qubit);
                    }
                }
            });
        });
        if (!returnFinalState && measurement.isEmpty()) {
            throw new IllegalArgumentException(
                    "simulation must request the final state or provide a measurement instruction");
        }
    }

    public static SimulationRequest zeroState(QuantumCircuit circuit) {
        return new SimulationRequest(circuit, Optional.empty(), Optional.empty(), true);
    }

    public static SimulationRequest withInitialState(QuantumCircuit circuit, StateVector state) {
        Objects.requireNonNull(state, "state");
        return new SimulationRequest(circuit, Optional.of(state), Optional.empty(), true);
    }

    public SimulationRequest withMeasurement(MeasurementInstruction instruction) {
        Objects.requireNonNull(instruction, "instruction");
        return new SimulationRequest(circuit, initialState, Optional.of(instruction), returnFinalState);
    }

    public SimulationRequest withMeasurement(MeasurementInstruction instruction, boolean includeFinalState) {
        Objects.requireNonNull(instruction, "instruction");
        return new SimulationRequest(circuit, initialState, Optional.of(instruction), includeFinalState);
    }

    public SimulationRequest withoutFinalState() {
        if (measurement.isEmpty()) {
            throw new IllegalStateException("cannot drop final state without measurement instruction");
        }
        if (!returnFinalState) {
            return this;
        }
        return new SimulationRequest(circuit, initialState, measurement, false);
    }
}
