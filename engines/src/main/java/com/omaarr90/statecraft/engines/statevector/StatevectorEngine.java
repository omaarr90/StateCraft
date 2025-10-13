package com.omaarr90.statecraft.engines.statevector;

import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import com.omaarr90.statecraft.quantum.SingleQubitGate;
import java.util.Objects;

/**
 * Simulator engine backed by SIMD-enhanced statevector kernels.
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
        int qubitCount = circuit.qubitCount();
        int dimension = 1 << qubitCount;
        double[] real = new double[dimension];
        double[] imag = new double[dimension];

        request.initialState().ifPresentOrElse(
                state -> populateFromState(state, real, imag),
                () -> resetZeroState(real, imag));

        for (QuantumCircuit.Operation operation : circuit.operations()) {
            if (operation instanceof QuantumCircuit.Operation.SingleGateOperation single) {
                applySingle(real, imag, single);
            } else if (operation instanceof QuantumCircuit.Operation.CnotOperation cnot) {
                StatevectorKernels.applyCnot(real, imag, cnot.controlQubit(), cnot.targetQubit());
            } else {
                throw new IllegalStateException("Unsupported operation type: " + operation.getClass().getName());
            }
        }

        StateVector finalState = toStateVector(qubitCount, real, imag);
        return new SimulationResult(finalState);
    }

    private void applySingle(double[] real, double[] imag, QuantumCircuit.Operation.SingleGateOperation single) {
        SingleQubitGate gate = single.gate();
        double g00r = gate.element(0, 0).real();
        double g00i = gate.element(0, 0).imag();
        double g01r = gate.element(0, 1).real();
        double g01i = gate.element(0, 1).imag();
        double g10r = gate.element(1, 0).real();
        double g10i = gate.element(1, 0).imag();
        double g11r = gate.element(1, 1).real();
        double g11i = gate.element(1, 1).imag();
        StatevectorKernels.applySingleGate(real, imag, single.qubit(),
                g00r, g00i, g01r, g01i, g10r, g10i, g11r, g11i);
    }

    private static void populateFromState(StateVector state, double[] real, double[] imag) {
        double[] data = state.data();
        int dimension = state.dimension();
        for (int index = 0; index < dimension; index++) {
            int base = index << 1;
            real[index] = data[base];
            imag[index] = data[base + 1];
        }
    }

    private static void resetZeroState(double[] real, double[] imag) {
        real[0] = 1.0;
        imag[0] = 0.0;
        for (int i = 1; i < real.length; i++) {
            real[i] = 0.0;
            imag[i] = 0.0;
        }
    }

    private static StateVector toStateVector(int qubitCount, double[] real, double[] imag) {
        int dimension = real.length;
        double[] data = new double[dimension << 1];
        for (int index = 0; index < dimension; index++) {
            int base = index << 1;
            data[base] = real[index];
            data[base + 1] = imag[index];
        }
        return StateVector.fromArray(qubitCount, data);
    }
}
