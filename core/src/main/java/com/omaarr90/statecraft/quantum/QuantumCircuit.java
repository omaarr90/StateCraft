package com.omaarr90.statecraft.quantum;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record QuantumCircuit(int qubitCount, List<QuantumCircuit.Operation> operations) {

    private static final ComplexNumber ZERO = ComplexNumber.zero();

    public QuantumCircuit(int qubitCount) {
        this(qubitCount, List.of());
    }

    public QuantumCircuit {
        if (qubitCount <= 0) {
            throw new IllegalArgumentException("qubitCount must be positive");
        }
        operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
        for (Operation operation : operations) {
            Objects.requireNonNull(operation, "operation");
            operation.validateTargets(qubitCount);
        }
    }

    public QuantumCircuit append(SingleQubitGate gate, int targetQubit) {
        Operation.SingleGateOperation application =
                new Operation.SingleGateOperation(Objects.requireNonNull(gate, "gate"), targetQubit);
        application.validateTargets(qubitCount);
        List<Operation> next = new ArrayList<>(operations);
        next.add(application);
        return new QuantumCircuit(qubitCount, next);
    }

    public QuantumCircuit append(CnotGate gate, int controlQubit, int targetQubit) {
        Operation.CnotOperation application = new Operation.CnotOperation(
                Objects.requireNonNull(gate, "gate"), controlQubit, targetQubit);
        application.validateTargets(qubitCount);
        List<Operation> next = new ArrayList<>(operations);
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
            throw new IllegalArgumentException(
                    "Expected state vector of length " + dimension + ", got " + initialState.length);
        }
        ComplexNumber[] state = new ComplexNumber[dimension];
        for (int i = 0; i < dimension; i++) {
            state[i] = initialState[i] == null ? ZERO : initialState[i];
        }
        for (Operation operation : operations) {
            applyOperation(state, operation);
        }
        return state;
    }

    private void applyOperation(ComplexNumber[] state, Operation operation) {
        if (operation instanceof Operation.SingleGateOperation single) {
            applySingleGate(state, single);
        } else if (operation instanceof Operation.CnotOperation cnot) {
            applyCnot(state, cnot);
        } else {
            throw new IllegalStateException("Unsupported operation type: " + operation.getClass().getName());
        }
    }

    private void applySingleGate(ComplexNumber[] state, Operation.SingleGateOperation operation) {
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

    private void applyCnot(ComplexNumber[] state, Operation.CnotOperation operation) {
        int control = operation.controlQubit();
        int target = operation.targetQubit();
        int controlMask = 1 << control;
        int targetMask = 1 << target;
        int pairMask = controlMask | targetMask;
        for (int base = 0; base < state.length; base++) {
            if ((base & pairMask) == 0) {
                int idx00 = base;
                int idx01 = base | targetMask;
                int idx10 = base | controlMask;
                int idx11 = base | pairMask;

                ComplexNumber amp00 = state[idx00] == null ? ZERO : state[idx00];
                ComplexNumber amp01 = state[idx01] == null ? ZERO : state[idx01];
                ComplexNumber amp10 = state[idx10] == null ? ZERO : state[idx10];
                ComplexNumber amp11 = state[idx11] == null ? ZERO : state[idx11];

                state[idx00] = amp00;
                state[idx01] = amp01;
                state[idx10] = amp11;
                state[idx11] = amp10;
            }
        }
    }

    public sealed interface Operation permits Operation.SingleGateOperation, Operation.CnotOperation {

        void validateTargets(int qubitCount);

        record SingleGateOperation(SingleQubitGate gate, int qubit) implements Operation {

            public SingleGateOperation {
                Objects.requireNonNull(gate, "gate");
                if (qubit < 0) {
                    throw new IllegalArgumentException("qubit index must be non-negative");
                }
            }

            @Override
            public void validateTargets(int qubitCount) {
                if (qubit >= qubitCount) {
                    throw new IllegalArgumentException("target qubit out of range: " + qubit);
                }
            }
        }

        record CnotOperation(CnotGate gate, int controlQubit, int targetQubit) implements Operation {

            public CnotOperation {
                Objects.requireNonNull(gate, "gate");
                if (controlQubit < 0) {
                    throw new IllegalArgumentException("control qubit index must be non-negative");
                }
                if (targetQubit < 0) {
                    throw new IllegalArgumentException("target qubit index must be non-negative");
                }
                if (controlQubit == targetQubit) {
                    throw new IllegalArgumentException("control and target qubits must differ");
                }
            }

            @Override
            public void validateTargets(int qubitCount) {
                if (controlQubit >= qubitCount) {
                    throw new IllegalArgumentException("control qubit out of range: " + controlQubit);
                }
                if (targetQubit >= qubitCount) {
                    throw new IllegalArgumentException("target qubit out of range: " + targetQubit);
                }
            }
        }
    }
}
