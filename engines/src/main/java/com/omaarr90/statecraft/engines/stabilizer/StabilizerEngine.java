package com.omaarr90.statecraft.engines.stabilizer;

import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.engines.statevector.StatevectorEngine;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.PauliY;
import com.omaarr90.statecraft.quantum.PauliZ;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.SingleQubitGate;
import java.util.Objects;

/**
 * Clifford-focused simulator engine.
 *
 * <p>This backend currently validates that all circuit operations remain within
 * a stabilizer-friendly gate set, then executes the simulation via the shared
 * statevector backend for result materialization.
 */
public final class StabilizerEngine implements SimulatorEngine {

    public static final String ID = "stabilizer";
    private static final double EPS = 1e-12;

    private final StatevectorEngine delegate = new StatevectorEngine();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public SimulationResult simulate(SimulationRequest request) {
        Objects.requireNonNull(request, "request");
        ensureNoiseIsUnsupported(request);
        ensureCliffordCircuit(request.circuit());
        return delegate.simulate(request);
    }

    private static void ensureNoiseIsUnsupported(SimulationRequest request) {
        request.noiseModel().ifPresent(model -> {
            if (model.hasNoise()) {
                throw new UnsupportedOperationException(
                        "stabilizer engine does not support noisy simulation yet");
            }
        });
    }

    private static void ensureCliffordCircuit(QuantumCircuit circuit) {
        for (QuantumCircuit.Operation operation : circuit.operations()) {
            validateOperation(operation);
        }
    }

    private static void validateOperation(QuantumCircuit.Operation operation) {
        if (operation instanceof QuantumCircuit.Operation.SingleGateOperation single) {
            validateSingleGate(single.gate());
            return;
        }
        if (operation instanceof QuantumCircuit.Operation.CnotOperation) {
            return;
        }
        if (operation instanceof QuantumCircuit.Operation.SwapOperation) {
            return;
        }
        if (operation instanceof QuantumCircuit.Operation.MeasureOperation) {
            return;
        }
        if (operation instanceof QuantumCircuit.Operation.MultiControlOperation multi) {
            validateControlledPauli(multi);
            return;
        }
        if (operation instanceof QuantumCircuit.Operation.TwoQubitGateOperation) {
            throw new UnsupportedOperationException(
                    "stabilizer engine does not support arbitrary two-qubit unitaries");
        }
        if (operation instanceof QuantumCircuit.Operation.TwoQubitDiagonalOperation diagonal) {
            if (isSupportedDiagonalClifford(diagonal.diagonal())) {
                return;
            }
            throw new UnsupportedOperationException(
                    "stabilizer engine supports only identity/CZ-style diagonal two-qubit gates");
        }
        throw new UnsupportedOperationException(
                "stabilizer engine does not support operation type: " + operation.getClass().getName());
    }

    private static void validateSingleGate(SingleQubitGate gate) {
        if (gate instanceof Hadamard
                || gate instanceof PauliX
                || gate instanceof PauliY
                || gate instanceof PauliZ) {
            return;
        }
        throw new UnsupportedOperationException(
                "stabilizer engine supports only Clifford single-qubit gates (H, X, Y, Z); got " + gate.name());
    }

    private static void validateControlledPauli(QuantumCircuit.Operation.MultiControlOperation operation) {
        if (operation.controlQubits().length != 1) {
            throw new UnsupportedOperationException(
                    "stabilizer engine supports single-control Pauli gates only");
        }
        SingleQubitGate gate = operation.gate();
        if (gate instanceof PauliX || gate instanceof PauliY || gate instanceof PauliZ) {
            return;
        }
        throw new UnsupportedOperationException(
                "stabilizer engine supports controlled Pauli gates only; got controlled-" + gate.name());
    }

    private static boolean isSupportedDiagonalClifford(ComplexNumber[] diagonal) {
        if (diagonal.length != 4) {
            return false;
        }
        boolean identity = isApprox(diagonal[0], 1.0, 0.0)
                && isApprox(diagonal[1], 1.0, 0.0)
                && isApprox(diagonal[2], 1.0, 0.0)
                && isApprox(diagonal[3], 1.0, 0.0);
        boolean cz = isApprox(diagonal[0], 1.0, 0.0)
                && isApprox(diagonal[1], 1.0, 0.0)
                && isApprox(diagonal[2], 1.0, 0.0)
                && isApprox(diagonal[3], -1.0, 0.0);
        return identity || cz;
    }

    private static boolean isApprox(ComplexNumber value, double real, double imag) {
        return Math.abs(value.real() - real) <= EPS && Math.abs(value.imag() - imag) <= EPS;
    }
}
