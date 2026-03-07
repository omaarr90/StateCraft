package com.omaarr90.statecraft.engines.tensornetwork;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.MeasurementResult;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.quantum.SingleQubitGate;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * Tensor-network backend implemented as an MPS simulator with SVD truncation.
 */
public final class TensorNetworkEngine implements SimulatorEngine {

    public static final String ID = "tensornetwork";
    static final int MAX_QUBITS = 50;
    static final int MAX_DEPTH = 40;
    static final int MAX_BOND_DIMENSION = 256;
    static final double SINGULAR_CUTOFF = 1e-10;
    static final int MAX_FINAL_STATE_QUBITS = 20;
    static final int MAX_INITIAL_STATE_QUBITS = 20;
    private static final double EPS = 1e-12;

    private final ComplexSvdAdapter svdAdapter;
    private final int maxBondDimension;
    private final double singularCutoff;

    public TensorNetworkEngine() {
        this(new EjmlComplexSvdAdapter(), MAX_BOND_DIMENSION, SINGULAR_CUTOFF);
    }

    TensorNetworkEngine(ComplexSvdAdapter svdAdapter, int maxBondDimension, double singularCutoff) {
        this.svdAdapter = Objects.requireNonNull(svdAdapter, "svdAdapter");
        if (maxBondDimension <= 0) {
            throw new IllegalArgumentException("maxBondDimension must be positive");
        }
        if (!Double.isFinite(singularCutoff) || singularCutoff < 0.0) {
            throw new IllegalArgumentException("singularCutoff must be finite and non-negative");
        }
        this.maxBondDimension = maxBondDimension;
        this.singularCutoff = singularCutoff;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public SimulationResult simulate(SimulationRequest request) {
        Objects.requireNonNull(request, "request");

        ensureNoNoise(request);
        QuantumCircuit circuit = request.circuit();
        ensureSupportedQubitCount(circuit.qubitCount());
        ensureShallowDepth(circuit);

        MpsState state = createInitialState(request);
        List<QuantumCircuit.Operation.MeasureOperation> measureOperations = new ArrayList<>();
        boolean measurementSeen = false;

        for (QuantumCircuit.Operation operation : circuit.operations()) {
            if (operation instanceof QuantumCircuit.Operation.MeasureOperation measure) {
                measurementSeen = true;
                measureOperations.add(measure);
                continue;
            }
            if (measurementSeen) {
                throw new UnsupportedOperationException(
                        "Unitary operations cannot follow measurement operations in the circuit");
            }
            applyOperation(state, operation);
        }

        Optional<StateVector> finalState = request.returnFinalState()
                ? Optional.of(materializeFinalState(state, circuit.qubitCount()))
                : Optional.empty();
        Optional<MeasurementResult> measurement = request.measurement()
                .map(instruction -> performMeasurement(state, circuit.qubitCount(), instruction, measureOperations));

        if (finalState.isPresent() && measurement.isPresent()) {
            return SimulationResult.forStateAndMeasurement(finalState.get(), measurement.get());
        }
        if (finalState.isPresent()) {
            return SimulationResult.forState(finalState.get());
        }
        return SimulationResult.forMeasurement(measurement.orElseThrow(() ->
                new IllegalStateException("measurement instruction required when final state is omitted")));
    }

    private MpsState createInitialState(SimulationRequest request) {
        if (request.initialState().isPresent()) {
            StateVector stateVector = request.initialState().orElseThrow();
            if (stateVector.qubitCount() > MAX_INITIAL_STATE_QUBITS) {
                throw new UnsupportedOperationException(
                        "tensornetwork engine v1 supports dense initial states up to "
                                + MAX_INITIAL_STATE_QUBITS + " qubits, got " + stateVector.qubitCount());
            }
            return MpsState.fromStateVector(stateVector, svdAdapter, maxBondDimension, singularCutoff);
        }
        if (request.basisStateQubits().isPresent()) {
            return MpsState.basisState(
                    request.circuit().qubitCount(),
                    request.basisStateQubits().orElseThrow(),
                    svdAdapter,
                    maxBondDimension,
                    singularCutoff);
        }
        return MpsState.zeroState(request.circuit().qubitCount(), svdAdapter, maxBondDimension, singularCutoff);
    }

    private static StateVector materializeFinalState(MpsState state, int qubitCount) {
        if (qubitCount > MAX_FINAL_STATE_QUBITS) {
            throw new UnsupportedOperationException(
                    "tensornetwork engine cannot materialize amplitudes above "
                            + MAX_FINAL_STATE_QUBITS
                            + " qubits; request shots only or use the statevector engine");
        }
        return state.toStateVectorLogicalOrder();
    }

    private static void applyOperation(MpsState state, QuantumCircuit.Operation operation) {
        if (operation instanceof QuantumCircuit.Operation.SingleGateOperation single) {
            applySingle(state, single.gate(), single.qubit());
            return;
        }
        if (operation instanceof QuantumCircuit.Operation.CnotOperation cnot) {
            state.applyCnot(cnot.controlQubit(), cnot.targetQubit());
            return;
        }
        if (operation instanceof QuantumCircuit.Operation.TwoQubitDiagonalOperation diagonal) {
            state.applyDiagonal(diagonal.firstQubit(), diagonal.secondQubit(), diagonal.diagonal());
            return;
        }
        if (operation instanceof QuantumCircuit.Operation.SwapOperation swap) {
            state.applySwapGate(swap.firstQubit(), swap.secondQubit());
            return;
        }
        if (operation instanceof QuantumCircuit.Operation.TwoQubitGateOperation) {
            throw new UnsupportedOperationException(
                    "tensornetwork engine v1 does not support arbitrary two-qubit unitaries");
        }
        if (operation instanceof QuantumCircuit.Operation.MultiControlOperation) {
            throw new UnsupportedOperationException(
                    "tensornetwork engine v1 does not support multi-control gates");
        }
        throw new UnsupportedOperationException(
                "tensornetwork engine does not support operation type: " + operation.getClass().getName());
    }

    private static void applySingle(MpsState state, SingleQubitGate gate, int qubit) {
        state.applySingleGate(qubit, gate);
    }

    private static MeasurementResult performMeasurement(
            MpsState state,
            int totalQubits,
            MeasurementInstruction instruction,
            List<QuantumCircuit.Operation.MeasureOperation> measureOperations) {
        int[] measuredQubits = resolveMeasuredQubits(totalQubits, instruction, measureOperations);
        if (measuredQubits.length == 0) {
            throw new IllegalStateException("No qubits available for measurement");
        }
        SplittableRandom rng = instruction.seed().isPresent()
                ? new SplittableRandom(instruction.seed().getAsLong())
                : new SplittableRandom();
        if (measuredQubits.length <= Integer.SIZE - 1) {
            return sampleSmallMeasurement(state, instruction, measuredQubits, rng);
        }
        return sampleBitstringMeasurement(state, instruction, measuredQubits, rng);
    }

    private static MeasurementResult sampleSmallMeasurement(
            MpsState state,
            MeasurementInstruction instruction,
            int[] measuredQubits,
            SplittableRandom rng) {
        int shots = instruction.shots();
        int[] bitIndexByPhysical = buildBitIndexByPhysical(state, measuredQubits);
        double[][] rightEnvironments = buildRightEnvironments(state);
        if (instruction.mode() == MeasurementInstruction.Mode.COUNTS) {
            Map<Integer, Integer> counts = new HashMap<>();
            for (int shot = 0; shot < shots; shot++) {
                int[] bits = sampleShotBits(state, measuredQubits.length, bitIndexByPhysical, rightEnvironments, rng);
                counts.merge(packOutcome(bits), 1, Integer::sum);
            }
            return new MeasurementResult.Histogram(measuredQubits, shots, counts);
        }
        List<Integer> samples = new ArrayList<>(shots);
        for (int shot = 0; shot < shots; shot++) {
            int[] bits = sampleShotBits(state, measuredQubits.length, bitIndexByPhysical, rightEnvironments, rng);
            samples.add(packOutcome(bits));
        }
        return new MeasurementResult.Samples(measuredQubits, shots, samples);
    }

    private static MeasurementResult sampleBitstringMeasurement(
            MpsState state,
            MeasurementInstruction instruction,
            int[] measuredQubits,
            SplittableRandom rng) {
        int shots = instruction.shots();
        int[] bitIndexByPhysical = buildBitIndexByPhysical(state, measuredQubits);
        double[][] rightEnvironments = buildRightEnvironments(state);
        if (instruction.mode() == MeasurementInstruction.Mode.COUNTS) {
            Map<String, Integer> counts = new HashMap<>();
            for (int shot = 0; shot < shots; shot++) {
                int[] bits = sampleShotBits(state, measuredQubits.length, bitIndexByPhysical, rightEnvironments, rng);
                counts.merge(packBitstringOutcome(bits), 1, Integer::sum);
            }
            return new MeasurementResult.BitstringHistogram(measuredQubits, shots, counts);
        }
        List<String> samples = new ArrayList<>(shots);
        for (int shot = 0; shot < shots; shot++) {
            int[] bits = sampleShotBits(state, measuredQubits.length, bitIndexByPhysical, rightEnvironments, rng);
            samples.add(packBitstringOutcome(bits));
        }
        return new MeasurementResult.BitstringSamples(measuredQubits, shots, samples);
    }

    private static int[] sampleShotBits(
            MpsState state,
            int measuredQubitCount,
            int[] bitIndexByPhysical,
            double[][] rightEnvironments,
            SplittableRandom rng) {
        int qubitCount = state.qubitCount();
        double[] leftEnvironment = new double[] {1.0, 0.0};
        int[] sampledBits = new int[measuredQubitCount];

        for (int physical = 0; physical < qubitCount; physical++) {
            MpsTensor tensor = state.tensorAtPhysical(physical);
            int measuredIndex = bitIndexByPhysical[physical];
            if (measuredIndex < 0) {
                leftEnvironment = propagateLeftFull(leftEnvironment, tensor);
                continue;
            }

            double[] projectedZero = propagateLeftProjected(leftEnvironment, tensor, 0);
            double[] projectedOne = propagateLeftProjected(leftEnvironment, tensor, 1);
            double probabilityZero = Math.max(0.0, contract(projectedZero, rightEnvironments[physical + 1]));
            double probabilityOne = Math.max(0.0, contract(projectedOne, rightEnvironments[physical + 1]));
            double probabilitySum = probabilityZero + probabilityOne;
            if (probabilitySum <= EPS) {
                throw new IllegalStateException("State collapsed to zero probability amplitudes");
            }

            double threshold = probabilityOne / probabilitySum;
            int bit = rng.nextDouble() < threshold ? 1 : 0;
            double[] chosen = bit == 0 ? projectedZero : projectedOne;
            double chosenProbability = bit == 0 ? probabilityZero : probabilityOne;
            if (chosenProbability <= EPS) {
                bit = 1 - bit;
                chosen = bit == 0 ? projectedZero : projectedOne;
                chosenProbability = bit == 0 ? probabilityZero : probabilityOne;
            }
            if (chosenProbability <= EPS) {
                throw new IllegalStateException("State collapsed to zero probability amplitudes");
            }

            sampledBits[measuredIndex] = bit;
            leftEnvironment = scale(chosen, 1.0 / chosenProbability);
        }
        return sampledBits;
    }

    private static int[] buildBitIndexByPhysical(MpsState state, int[] measuredLogicalQubits) {
        int[] bitIndexByPhysical = new int[state.qubitCount()];
        Arrays.fill(bitIndexByPhysical, -1);
        for (int index = 0; index < measuredLogicalQubits.length; index++) {
            bitIndexByPhysical[state.physicalOfLogical(measuredLogicalQubits[index])] = index;
        }
        return bitIndexByPhysical;
    }

    private static double[][] buildRightEnvironments(MpsState state) {
        int qubitCount = state.qubitCount();
        double[][] environments = new double[qubitCount + 1][];
        environments[qubitCount] = new double[] {1.0, 0.0};
        for (int physical = qubitCount - 1; physical >= 0; physical--) {
            environments[physical] = propagateRightFull(state.tensorAtPhysical(physical), environments[physical + 1]);
        }
        return environments;
    }

    private static double[] propagateLeftFull(double[] leftEnvironment, MpsTensor tensor) {
        double[] zero = propagateLeftProjected(leftEnvironment, tensor, 0);
        double[] one = propagateLeftProjected(leftEnvironment, tensor, 1);
        double[] out = new double[zero.length];
        for (int index = 0; index < out.length; index++) {
            out[index] = zero[index] + one[index];
        }
        return out;
    }

    private static double[] propagateLeftProjected(double[] leftEnvironment, MpsTensor tensor, int bit) {
        int leftDim = tensor.leftDim;
        int rightDim = tensor.rightDim;
        double[] temp = new double[leftDim * rightDim * 2];

        for (int leftPrime = 0; leftPrime < leftDim; leftPrime++) {
            for (int right = 0; right < rightDim; right++) {
                double sumReal = 0.0;
                double sumImag = 0.0;
                for (int left = 0; left < leftDim; left++) {
                    int leftIndex = complexIndex(left, leftPrime, leftDim);
                    double lr = leftEnvironment[leftIndex];
                    double li = leftEnvironment[leftIndex + 1];
                    double ar = tensor.real(left, bit, right);
                    double ai = tensor.imag(left, bit, right);
                    sumReal += (lr * ar) - (li * ai);
                    sumImag += (lr * ai) + (li * ar);
                }
                int tempIndex = complexIndex(leftPrime, right, rightDim);
                temp[tempIndex] = sumReal;
                temp[tempIndex + 1] = sumImag;
            }
        }

        double[] out = new double[rightDim * rightDim * 2];
        for (int right = 0; right < rightDim; right++) {
            for (int rightPrime = 0; rightPrime < rightDim; rightPrime++) {
                double sumReal = 0.0;
                double sumImag = 0.0;
                for (int leftPrime = 0; leftPrime < leftDim; leftPrime++) {
                    int tempIndex = complexIndex(leftPrime, right, rightDim);
                    double tr = temp[tempIndex];
                    double ti = temp[tempIndex + 1];
                    double ar = tensor.real(leftPrime, bit, rightPrime);
                    double ai = tensor.imag(leftPrime, bit, rightPrime);
                    sumReal += (tr * ar) + (ti * ai);
                    sumImag += (ti * ar) - (tr * ai);
                }
                int outIndex = complexIndex(right, rightPrime, rightDim);
                out[outIndex] = sumReal;
                out[outIndex + 1] = sumImag;
            }
        }
        return out;
    }

    private static double[] propagateRightFull(MpsTensor tensor, double[] rightEnvironment) {
        double[] zero = propagateRightProjected(tensor, rightEnvironment, 0);
        double[] one = propagateRightProjected(tensor, rightEnvironment, 1);
        double[] out = new double[zero.length];
        for (int index = 0; index < out.length; index++) {
            out[index] = zero[index] + one[index];
        }
        return out;
    }

    private static double[] propagateRightProjected(MpsTensor tensor, double[] rightEnvironment, int bit) {
        int leftDim = tensor.leftDim;
        int rightDim = tensor.rightDim;
        double[] temp = new double[leftDim * rightDim * 2];

        for (int left = 0; left < leftDim; left++) {
            for (int rightPrime = 0; rightPrime < rightDim; rightPrime++) {
                double sumReal = 0.0;
                double sumImag = 0.0;
                for (int right = 0; right < rightDim; right++) {
                    double ar = tensor.real(left, bit, right);
                    double ai = tensor.imag(left, bit, right);
                    int envIndex = complexIndex(right, rightPrime, rightDim);
                    double rr = rightEnvironment[envIndex];
                    double ri = rightEnvironment[envIndex + 1];
                    sumReal += (ar * rr) - (ai * ri);
                    sumImag += (ar * ri) + (ai * rr);
                }
                int tempIndex = complexIndex(left, rightPrime, rightDim);
                temp[tempIndex] = sumReal;
                temp[tempIndex + 1] = sumImag;
            }
        }

        double[] out = new double[leftDim * leftDim * 2];
        for (int left = 0; left < leftDim; left++) {
            for (int leftPrime = 0; leftPrime < leftDim; leftPrime++) {
                double sumReal = 0.0;
                double sumImag = 0.0;
                for (int rightPrime = 0; rightPrime < rightDim; rightPrime++) {
                    int tempIndex = complexIndex(left, rightPrime, rightDim);
                    double tr = temp[tempIndex];
                    double ti = temp[tempIndex + 1];
                    double ar = tensor.real(leftPrime, bit, rightPrime);
                    double ai = tensor.imag(leftPrime, bit, rightPrime);
                    sumReal += (tr * ar) + (ti * ai);
                    sumImag += (ti * ar) - (tr * ai);
                }
                int outIndex = complexIndex(left, leftPrime, leftDim);
                out[outIndex] = sumReal;
                out[outIndex + 1] = sumImag;
            }
        }
        return out;
    }

    private static double contract(double[] left, double[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("environment dimensions mismatch");
        }
        double sumReal = 0.0;
        double sumImag = 0.0;
        for (int index = 0; index < left.length; index += 2) {
            double lr = left[index];
            double li = left[index + 1];
            double rr = right[index];
            double ri = right[index + 1];
            sumReal += (lr * rr) - (li * ri);
            sumImag += (lr * ri) + (li * rr);
        }
        if (Math.abs(sumImag) > 1e-8) {
            throw new IllegalStateException("Encountered complex-valued probability during measurement");
        }
        return sumReal;
    }

    private static double[] scale(double[] matrix, double factor) {
        double[] out = new double[matrix.length];
        for (int index = 0; index < matrix.length; index++) {
            out[index] = matrix[index] * factor;
        }
        return out;
    }

    private static int packOutcome(int[] bits) {
        int outcome = 0;
        for (int index = 0; index < bits.length; index++) {
            if (bits[index] == 1) {
                outcome |= 1 << index;
            }
        }
        return outcome;
    }

    private static String packBitstringOutcome(int[] bits) {
        StringBuilder builder = new StringBuilder(bits.length);
        for (int index = bits.length - 1; index >= 0; index--) {
            builder.append(bits[index] == 0 ? '0' : '1');
        }
        return builder.toString();
    }

    private static void ensureNoNoise(SimulationRequest request) {
        request.noiseModel().ifPresent(model -> {
            if (model.hasNoise()) {
                throw new UnsupportedOperationException(
                        "tensornetwork engine does not support noisy simulation yet");
            }
        });
    }

    private static void ensureSupportedQubitCount(int qubitCount) {
        if (qubitCount > MAX_QUBITS) {
            throw new UnsupportedOperationException(
                    "tensornetwork engine supports up to "
                            + MAX_QUBITS + " qubits, got " + qubitCount);
        }
    }

    private static void ensureShallowDepth(QuantumCircuit circuit) {
        int depth = estimateDepth(circuit);
        if (depth > MAX_DEPTH) {
            throw new UnsupportedOperationException(
                    "tensornetwork engine supports shallow circuits only (depth <= "
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

    private static int[] resolveMeasuredQubits(
            int totalQubits,
            MeasurementInstruction instruction,
            List<QuantumCircuit.Operation.MeasureOperation> measureOperations) {
        return instruction.measuredQubits()
                .map(int[]::clone)
                .orElseGet(() -> {
                    if (!measureOperations.isEmpty()) {
                        return measureOperations.stream()
                                .flatMapToInt(operation -> Arrays.stream(operation.qubits()))
                                .distinct()
                                .sorted()
                                .toArray();
                    }
                    int[] allQubits = new int[totalQubits];
                    for (int index = 0; index < totalQubits; index++) {
                        allQubits[index] = index;
                    }
                    return allQubits;
                });
    }

    private static int complexIndex(int row, int col, int cols) {
        return ((row * cols) + col) << 1;
    }
}
