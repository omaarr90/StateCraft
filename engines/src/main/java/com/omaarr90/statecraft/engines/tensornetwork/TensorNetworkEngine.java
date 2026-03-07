package com.omaarr90.statecraft.engines.tensornetwork;

import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.engines.statevector.StatevectorEngine;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import java.util.Objects;

/**
 * Tensor-network backend prototype for shallow circuits.
 *
 * <p>The current prototype validates tensor-network-friendly constraints
 * (circuit depth and supported operation kinds), then executes through the
 * shared simulation pipeline to preserve correctness while the dedicated
 * contraction kernels evolve.
 */
public final class TensorNetworkEngine implements SimulatorEngine {

    public static final String ID = "tensornetwork";

    /**
     * Prototype constraints: no noise and shallow depth only.
     */
    static final int MAX_DEPTH = 40;
    static final int MAX_QUBITS = 28;

    private final StatevectorEngine delegate = new StatevectorEngine();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public SimulationResult simulate(SimulationRequest request) {
        Objects.requireNonNull(request, "request");

        ensureNoNoise(request);
        ensureSupportedQubitCount(request.circuit().qubitCount());
        ensureSupportedOperations(request.circuit());
        ensureShallowDepth(request.circuit());

        return delegate.simulate(request);
    }

    private static void ensureNoNoise(SimulationRequest request) {
        request.noiseModel().ifPresent(model -> {
            if (model.hasNoise()) {
                throw new UnsupportedOperationException(
                        "tensornetwork engine prototype does not support noisy simulation");
            }
        });
    }

    private static void ensureSupportedQubitCount(int qubitCount) {
        if (qubitCount > MAX_QUBITS) {
            throw new UnsupportedOperationException(
                    "tensornetwork prototype currently supports up to "
                            + MAX_QUBITS + " qubits, got " + qubitCount);
        }
    }

    private static void ensureSupportedOperations(QuantumCircuit circuit) {
        for (QuantumCircuit.Operation operation : circuit.operations()) {
            validateOperation(operation);
        }
    }

    private static void validateOperation(QuantumCircuit.Operation operation) {
        if (operation instanceof QuantumCircuit.Operation.SingleGateOperation
                || operation instanceof QuantumCircuit.Operation.CnotOperation
                || operation instanceof QuantumCircuit.Operation.TwoQubitGateOperation
                || operation instanceof QuantumCircuit.Operation.TwoQubitDiagonalOperation
                || operation instanceof QuantumCircuit.Operation.SwapOperation
                || operation instanceof QuantumCircuit.Operation.MultiControlOperation
                || operation instanceof QuantumCircuit.Operation.MeasureOperation) {
            return;
        }
        throw new UnsupportedOperationException(
                "tensornetwork engine does not support operation type: "
                        + operation.getClass().getName());
    }

    private static void ensureShallowDepth(QuantumCircuit circuit) {
        int depth = estimateDepth(circuit);
        if (depth > MAX_DEPTH) {
            throw new UnsupportedOperationException(
                    "tensornetwork prototype supports shallow circuits only (depth <= "
                            + MAX_DEPTH + "), got " + depth);
        }
    }

    private static int estimateDepth(QuantumCircuit circuit) {
        int qubits = circuit.qubitCount();
        int[] layers = new int[qubits];
        int maxDepth = 0;

        for (QuantumCircuit.Operation operation : circuit.operations()) {
            int[] touched = touchedQubits(operation);
            int startLayer = 1;
            for (int qubit : touched) {
                startLayer = Math.max(startLayer, layers[qubit] + 1);
            }
            for (int qubit : touched) {
                layers[qubit] = startLayer;
            }
            maxDepth = Math.max(maxDepth, startLayer);
        }
        return maxDepth;
    }

    private static int[] touchedQubits(QuantumCircuit.Operation operation) {
        if (operation instanceof QuantumCircuit.Operation.SingleGateOperation single) {
            return new int[] {single.qubit()};
        }
        if (operation instanceof QuantumCircuit.Operation.CnotOperation cnot) {
            return new int[] {cnot.controlQubit(), cnot.targetQubit()};
        }
        if (operation instanceof QuantumCircuit.Operation.TwoQubitGateOperation two) {
            return new int[] {two.firstQubit(), two.secondQubit()};
        }
        if (operation instanceof QuantumCircuit.Operation.TwoQubitDiagonalOperation diagonal) {
            return new int[] {diagonal.firstQubit(), diagonal.secondQubit()};
        }
        if (operation instanceof QuantumCircuit.Operation.SwapOperation swap) {
            return new int[] {swap.firstQubit(), swap.secondQubit()};
        }
        if (operation instanceof QuantumCircuit.Operation.MultiControlOperation multi) {
            int[] controls = multi.controlQubits();
            int[] touched = new int[controls.length + 1];
            System.arraycopy(controls, 0, touched, 0, controls.length);
            touched[controls.length] = multi.targetQubit();
            return touched;
        }
        if (operation instanceof QuantumCircuit.Operation.MeasureOperation measure) {
            return measure.qubits();
        }
        throw new IllegalStateException("Unsupported operation type: " + operation.getClass().getName());
    }
}
