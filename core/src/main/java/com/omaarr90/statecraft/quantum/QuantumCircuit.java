package com.omaarr90.statecraft.quantum;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record QuantumCircuit(int qubitCount, List<GateApplication> operations) {

    private static final ComplexNumber ZERO = ComplexNumber.zero();

    public QuantumCircuit(int qubitCount) {
        this(qubitCount, List.of());
    }

    public QuantumCircuit {
        if (qubitCount <= 0) {
            throw new IllegalArgumentException("qubitCount must be positive");
        }
        operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
        for (GateApplication operation : operations) {
            validateTarget(qubitCount, operation.qubit());
        }
    }

    public QuantumCircuit append(SingleQubitGate gate, int targetQubit) {
        GateApplication application = new GateApplication(Objects.requireNonNull(gate, "gate"), targetQubit);
        validateTarget(qubitCount, application.qubit());
        List<GateApplication> next = new ArrayList<>(operations);
        next.add(application);
        return new QuantumCircuit(qubitCount, next);
    }

    public ComplexNumber[] apply() {
        int dimension = 1 << qubitCount;
        ComplexNumber[] state = new ComplexNumber[dimension];
        state[0] = ComplexNumber.one();
        for (int i = 1; i < dimension; i++) {
            state[i] = ZERO;
        }
        return apply(state);
    }

    public ComplexNumber[] apply(ComplexNumber[] initialState) {
        Objects.requireNonNull(initialState, "initialState");
        int dimension = 1 << qubitCount;
        if (initialState.length != dimension) {
            throw new IllegalArgumentException("Expected state vector of length " + dimension + ", got " + initialState.length);
        }
        ComplexNumber[] state = new ComplexNumber[dimension];
        for (int i = 0; i < dimension; i++) {
            state[i] = initialState[i] == null ? ZERO : initialState[i];
        }
        for (GateApplication operation : operations) {
            applyGate(state, operation);
        }
        return state;
    }

    private void applyGate(ComplexNumber[] state, GateApplication operation) {
        int target = operation.qubit();
        SingleQubitGate gate = operation.gate();
        ComplexNumber g00 = gate.element(0, 0);
        ComplexNumber g01 = gate.element(0, 1);
        ComplexNumber g10 = gate.element(1, 0);
        ComplexNumber g11 = gate.element(1, 1);
        int stride = 1 << target;
        int period = stride << 1;
        for (int base = 0; base < state.length; base += period) {
            for (int offset = 0; offset < stride; offset++) {
                int idx0 = base + offset;
                int idx1 = idx0 + stride;
                ComplexNumber alpha0 = state[idx0] == null ? ZERO : state[idx0];
                ComplexNumber alpha1 = state[idx1] == null ? ZERO : state[idx1];
                ComplexNumber new0 = g00.times(alpha0).plus(g01.times(alpha1));
                ComplexNumber new1 = g10.times(alpha0).plus(g11.times(alpha1));
                state[idx0] = new0;
                state[idx1] = new1;
            }
        }
    }

    private void requireValidTarget(int qubit) {
        validateTarget(qubitCount, qubit);
    }

    private static void validateTarget(int qubitCount, int qubit) {
        if (qubit < 0 || qubit >= qubitCount) {
            throw new IllegalArgumentException("target qubit out of range: " + qubit);
        }
    }

    public record GateApplication(SingleQubitGate gate, int qubit) {

        public GateApplication {
            Objects.requireNonNull(gate, "gate");
            if (qubit < 0) {
                throw new IllegalArgumentException("qubit index must be non-negative");
            }
        }
    }
}
