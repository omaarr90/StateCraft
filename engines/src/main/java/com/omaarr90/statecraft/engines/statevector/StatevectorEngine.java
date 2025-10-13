package com.omaarr90.statecraft.engines.statevector;

import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.Objects;

/**
 * Simple simulator engine that evolves circuits by delegating to {@link QuantumCircuit}
 * and returns the resulting amplitudes as a {@link StateVector}.
 */
public final class StatevectorEngine implements SimulatorEngine {

    public static final String ID = "statevector";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public SimulationResult simulate(SimulationRequest request) {
        Objects.requireNonNull(request, "request");
        QuantumCircuit circuit = request.circuit();
        ComplexNumber[] finalAmplitudes = request.initialState()
                .map(state -> circuit.apply(toComplexArray(state)))
                .orElseGet(circuit::apply);
        StateVector stateVector = toStateVector(circuit.qubitCount(), finalAmplitudes);
        return new SimulationResult(stateVector);
    }

    private static ComplexNumber[] toComplexArray(StateVector state) {
        int dimension = state.dimension();
        ComplexNumber[] vector = new ComplexNumber[dimension];
        double[] data = state.data();
        for (int index = 0; index < dimension; index++) {
            int base = index << 1;
            vector[index] = new ComplexNumber(data[base], data[base + 1]);
        }
        return vector;
    }

    private static StateVector toStateVector(int qubitCount, ComplexNumber[] amplitudes) {
        double[] data = new double[amplitudes.length << 1];
        for (int index = 0; index < amplitudes.length; index++) {
            ComplexNumber amplitude = amplitudes[index];
            if (amplitude == null) {
                continue;
            }
            int base = index << 1;
            data[base] = amplitude.real();
            data[base + 1] = amplitude.imag();
        }
        return StateVector.fromArray(qubitCount, data);
    }
}
