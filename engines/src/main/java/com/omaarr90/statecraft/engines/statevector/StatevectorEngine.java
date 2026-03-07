package com.omaarr90.statecraft.engines.statevector;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.MeasurementResult;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.core.math.ComplexArrays;
import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.core.noise.CompositeChannel;
import com.omaarr90.statecraft.core.noise.ErrorChannel;
import com.omaarr90.statecraft.core.noise.KrausDecomposition;
import com.omaarr90.statecraft.core.noise.KrausOperator;
import com.omaarr90.statecraft.core.noise.NoiseModel;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import com.omaarr90.statecraft.quantum.SingleQubitGate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

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
        double[] state = new double[dimension << 1];

        if (request.initialState().isPresent()) {
            populateFromState(request.initialState().orElseThrow(), state);
        } else if (request.basisStateQubits().isPresent()) {
            resetBasisState(state, request.basisStateQubits().orElseThrow());
        } else {
            resetZeroState(state);
        }

        NoiseModel noiseModel = request.noiseModel().orElse(null);
        boolean applyNoise = noiseModel != null && noiseModel.hasNoise();
        SplittableRandom noiseRng = applyNoise
                ? (request.noiseSeed().isPresent()
                        ? new SplittableRandom(request.noiseSeed().getAsLong())
                        : new SplittableRandom())
                : null;

        List<QuantumCircuit.Operation.MeasureOperation> measureOperations = new ArrayList<>();
        boolean measurementSeen = false;

        for (QuantumCircuit.Operation operation : circuit.operations()) {
            if (operation instanceof QuantumCircuit.Operation.SingleGateOperation single) {
                if (measurementSeen) {
                    throw new UnsupportedOperationException(
                            "Unitary operations cannot follow measurement operations in the circuit");
                }
                applySingle(state, single);
                if (applyNoise) {
                    applyNoiseAfterOperation(state, operation, noiseModel, noiseRng, qubitCount);
                }
            } else if (operation instanceof QuantumCircuit.Operation.CnotOperation cnot) {
                if (measurementSeen) {
                    throw new UnsupportedOperationException(
                            "Unitary operations cannot follow measurement operations in the circuit");
                }
                StatevectorKernels.applyCnot(state, cnot.controlQubit(), cnot.targetQubit());
                if (applyNoise) {
                    applyNoiseAfterOperation(state, operation, noiseModel, noiseRng, qubitCount);
                }
            } else if (operation instanceof QuantumCircuit.Operation.TwoQubitGateOperation twoQubit) {
                if (measurementSeen) {
                    throw new UnsupportedOperationException(
                            "Unitary operations cannot follow measurement operations in the circuit");
                }
                applyTwoQubitGate(state, twoQubit);
                if (applyNoise) {
                    applyNoiseAfterOperation(state, operation, noiseModel, noiseRng, qubitCount);
                }
            } else if (operation instanceof QuantumCircuit.Operation.TwoQubitDiagonalOperation diagonal) {
                if (measurementSeen) {
                    throw new UnsupportedOperationException(
                            "Unitary operations cannot follow measurement operations in the circuit");
                }
                applyTwoQubitDiagonal(state, diagonal);
                if (applyNoise) {
                    applyNoiseAfterOperation(state, operation, noiseModel, noiseRng, qubitCount);
                }
            } else if (operation instanceof QuantumCircuit.Operation.SwapOperation swap) {
                if (measurementSeen) {
                    throw new UnsupportedOperationException(
                            "Unitary operations cannot follow measurement operations in the circuit");
                }
                StatevectorKernels.applySwap(state, swap.firstQubit(), swap.secondQubit());
                if (applyNoise) {
                    applyNoiseAfterOperation(state, operation, noiseModel, noiseRng, qubitCount);
                }
            } else if (operation instanceof QuantumCircuit.Operation.MultiControlOperation multi) {
                if (measurementSeen) {
                    throw new UnsupportedOperationException(
                            "Unitary operations cannot follow measurement operations in the circuit");
                }
                applyMultiControl(state, multi);
                if (applyNoise) {
                    applyNoiseAfterOperation(state, operation, noiseModel, noiseRng, qubitCount);
                }
            } else if (operation instanceof QuantumCircuit.Operation.MeasureOperation measure) {
                measurementSeen = true;
                measureOperations.add(measure);
            } else {
                throw new IllegalStateException("Unsupported operation type: " + operation.getClass().getName());
            }
        }

        Optional<StateVector> finalState = request.returnFinalState()
                ? Optional.of(toStateVector(qubitCount, state))
                : Optional.empty();
        Optional<MeasurementResult> measurement = request.measurement()
                .map(instruction -> performMeasurement(state, qubitCount, instruction, measureOperations));

        if (finalState.isPresent() && measurement.isPresent()) {
            return SimulationResult.forStateAndMeasurement(finalState.get(), measurement.get());
        }
        if (finalState.isPresent()) {
            return SimulationResult.forState(finalState.get());
        }
        return SimulationResult.forMeasurement(measurement.orElseThrow(() ->
                new IllegalStateException("measurement instruction required when final state is omitted")));
    }

    private void applySingle(double[] state, QuantumCircuit.Operation.SingleGateOperation single) {
        SingleQubitGate gate = single.gate();
        double g00r = gate.element(0, 0).real();
        double g00i = gate.element(0, 0).imag();
        double g01r = gate.element(0, 1).real();
        double g01i = gate.element(0, 1).imag();
        double g10r = gate.element(1, 0).real();
        double g10i = gate.element(1, 0).imag();
        double g11r = gate.element(1, 1).real();
        double g11i = gate.element(1, 1).imag();
        StatevectorKernels.applySingleGate(state, single.qubit(),
                g00r, g00i, g01r, g01i, g10r, g10i, g11r, g11i);
    }

    private void applyTwoQubitGate(double[] state,
            QuantumCircuit.Operation.TwoQubitGateOperation operation) {
        ComplexNumber[] matrix = operation.matrix();
        double[] matrixReal = new double[matrix.length];
        double[] matrixImag = new double[matrix.length];
        for (int index = 0; index < matrix.length; index++) {
            ComplexNumber element = matrix[index];
            matrixReal[index] = element.real();
            matrixImag[index] = element.imag();
        }
        StatevectorKernels.applyTwoQubitUnitary(state,
                operation.firstQubit(), operation.secondQubit(), matrixReal, matrixImag);
    }

    private void applyTwoQubitDiagonal(double[] state,
            QuantumCircuit.Operation.TwoQubitDiagonalOperation operation) {
        ComplexNumber[] diagonal = operation.diagonal();
        double[] diagonalReal = new double[diagonal.length];
        double[] diagonalImag = new double[diagonal.length];
        for (int index = 0; index < diagonal.length; index++) {
            ComplexNumber element = diagonal[index];
            diagonalReal[index] = element.real();
            diagonalImag[index] = element.imag();
        }
        StatevectorKernels.applyTwoQubitDiagonal(state,
                operation.firstQubit(), operation.secondQubit(), diagonalReal, diagonalImag);
    }

    private void applyMultiControl(double[] state,
            QuantumCircuit.Operation.MultiControlOperation operation) {
        SingleQubitGate gate = operation.gate();
        double g00r = gate.element(0, 0).real();
        double g00i = gate.element(0, 0).imag();
        double g01r = gate.element(0, 1).real();
        double g01i = gate.element(0, 1).imag();
        double g10r = gate.element(1, 0).real();
        double g10i = gate.element(1, 0).imag();
        double g11r = gate.element(1, 1).real();
        double g11i = gate.element(1, 1).imag();
        StatevectorKernels.applyMultiControlledSingleGate(state, operation.targetQubit(),
                computeControlMask(operation.controlQubits()),
                g00r, g00i, g01r, g01i, g10r, g10i, g11r, g11i);
    }

    private MeasurementResult performMeasurement(double[] state, int totalQubits,
            MeasurementInstruction instruction, List<QuantumCircuit.Operation.MeasureOperation> measureOperations) {
        int[] measuredQubits = resolveMeasuredQubits(totalQubits, instruction, measureOperations);
        if (measuredQubits.length == 0) {
            throw new IllegalStateException("No qubits available for measurement");
        }
        double[] probabilities = computeMeasurementProbabilities(state, measuredQubits);
        normalize(probabilities);
        double[] cumulative = cumulative(probabilities);
        SplittableRandom rng = instruction.seed().isPresent()
                ? new SplittableRandom(instruction.seed().getAsLong())
                : new SplittableRandom();
        int shots = instruction.shots();
        if (instruction.mode() == MeasurementInstruction.Mode.COUNTS) {
            Map<Integer, Integer> counts = new HashMap<>();
            for (int shot = 0; shot < shots; shot++) {
                int outcome = sampleOutcome(cumulative, rng.nextDouble());
                counts.merge(outcome, 1, Integer::sum);
            }
            return new MeasurementResult.Histogram(measuredQubits, shots, counts);
        } else {
            List<Integer> samples = new ArrayList<>(shots);
            for (int shot = 0; shot < shots; shot++) {
                int outcome = sampleOutcome(cumulative, rng.nextDouble());
                samples.add(outcome);
            }
            return new MeasurementResult.Samples(measuredQubits, shots, samples);
        }
    }

    private static int[] resolveMeasuredQubits(int totalQubits, MeasurementInstruction instruction,
            List<QuantumCircuit.Operation.MeasureOperation> measureOperations) {
        return instruction.measuredQubits()
                .map(int[]::clone)
                .orElseGet(() -> {
                    if (!measureOperations.isEmpty()) {
                        return measureOperations.stream()
                                .flatMapToInt(op -> Arrays.stream(op.qubits()))
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

    private static double[] computeMeasurementProbabilities(double[] state, int[] measuredQubits) {
        int outcomeCount = 1 << measuredQubits.length;
        double[] probabilities = new double[outcomeCount];
        int dimension = state.length >> 1;
        for (int basis = 0; basis < dimension; basis++) {
            int base = basis << 1;
            double ampReal = state[base];
            double ampImag = state[base + 1];
            double probability = (ampReal * ampReal) + (ampImag * ampImag);
            if (probability == 0.0) {
                continue;
            }
            int outcome = 0;
            for (int bit = 0; bit < measuredQubits.length; bit++) {
                int qubit = measuredQubits[bit];
                if (((basis >> qubit) & 1) == 1) {
                    outcome |= (1 << bit);
                }
            }
            probabilities[outcome] += probability;
        }
        return probabilities;
    }

    private static void normalize(double[] probabilities) {
        double sum = 0.0;
        for (double probability : probabilities) {
            sum += probability;
        }
        if (sum == 0.0) {
            throw new IllegalStateException("State collapsed to zero probability amplitudes");
        }
        if (Math.abs(sum - 1.0) > 1e-12) {
            double scale = 1.0 / sum;
            for (int index = 0; index < probabilities.length; index++) {
                probabilities[index] *= scale;
            }
        }
    }

    private static double[] cumulative(double[] probabilities) {
        double[] cumulative = new double[probabilities.length];
        double running = 0.0;
        for (int index = 0; index < probabilities.length; index++) {
            running += probabilities[index];
            cumulative[index] = running;
        }
        cumulative[cumulative.length - 1] = 1.0;
        return cumulative;
    }

    private static int sampleOutcome(double[] cumulative, double value) {
        if (value >= 1.0) {
            return cumulative.length - 1;
        }
        int index = Arrays.binarySearch(cumulative, value);
        if (index >= 0) {
            while (index > 0 && cumulative[index - 1] >= value) {
                index--;
            }
            return index;
        }
        int insertionPoint = -index - 1;
        return insertionPoint;
    }

    private static void populateFromState(StateVector state, double[] target) {
        double[] data = state.copyData();
        System.arraycopy(data, 0, target, 0, data.length);
    }

    private static void resetZeroState(double[] state) {
        Arrays.fill(state, 0.0);
        state[0] = 1.0;
    }

    private static void resetBasisState(double[] state, int[] qubits) {
        Arrays.fill(state, 0.0);
        int basisIndex = 0;
        for (int qubit : qubits) {
            basisIndex |= 1 << qubit;
        }
        state[basisIndex << 1] = 1.0;
    }

    private static int computeControlMask(int[] controlQubits) {
        int mask = 0;
        for (int control : controlQubits) {
            mask |= 1 << control;
        }
        return mask;
    }

    private static StateVector toStateVector(int qubitCount, double[] state) {
        return StateVector.fromArray(qubitCount, state);
    }

    private void applyNoiseAfterOperation(double[] state, QuantumCircuit.Operation operation,
            NoiseModel noiseModel, SplittableRandom rng, int qubitCount) {
        List<NoiseModel.ScheduledChannel> channels = noiseModel.scheduledChannelsAfter(operation);
        if (channels.isEmpty()) {
            return;
        }
        for (NoiseModel.ScheduledChannel scheduledChannel : channels) {
            applyErrorChannel(state, scheduledChannel.channel(), scheduledChannel.targetQubits(), rng, qubitCount);
        }
    }

    private void applyErrorChannel(double[] state, ErrorChannel channel,
            int[] mappedQubits, SplittableRandom rng, int qubitCount) {
        if (channel instanceof CompositeChannel composite) {
            int[] componentMapping = mappedQubits != null && mappedQubits.length == 1
                    ? mappedQubits
                    : null;
            for (ErrorChannel component : composite.getChannels()) {
                applyErrorChannel(state, component, componentMapping, rng, qubitCount);
            }
            return;
        }
        int[] targets = resolveTargets(mappedQubits, channel, qubitCount);
        KrausDecomposition decomposition = channel.krausDecomposition();
        int decompositionQubits = decomposition.numQubits();
        if (decompositionQubits == 1) {
            for (int target : targets) {
                int operatorIndex = decomposition.sampleOperator(rng);
                KrausOperator operator = decomposition.operators().get(operatorIndex);
                applySingleQubitKraus(state, target, operator);
            }
            return;
        }
        if (decompositionQubits != targets.length) {
            throw new UnsupportedOperationException(
                    "Noise channel target/decomposition mismatch: decomposition qubits="
                            + decompositionQubits + ", targets=" + targets.length);
        }
        int operatorIndex = decomposition.sampleOperator(rng);
        KrausOperator operator = decomposition.operators().get(operatorIndex);
        applyMultiQubitKraus(state, targets, operator);
    }

    private static int[] resolveTargets(int[] mappedQubits, ErrorChannel channel, int qubitCount) {
        int[] targets = mappedQubits != null ? mappedQubits.clone() : channel.affectedQubits();
        if (targets.length == 0) {
            throw new IllegalArgumentException("noise channel must target at least one qubit");
        }
        Set<Integer> seen = new HashSet<>();
        for (int target : targets) {
            if (target < 0 || target >= qubitCount) {
                throw new IllegalArgumentException("noise channel qubit out of range: " + target);
            }
            if (!seen.add(target)) {
                throw new IllegalArgumentException("noise channel contains duplicate qubit: " + target);
            }
        }
        return targets;
    }

    private void applySingleQubitKraus(double[] state, int target, KrausOperator operator) {
        if (operator.numQubits() != 1) {
            throw new UnsupportedOperationException(
                    "Only single-qubit Kraus operators are supported, got " + operator.numQubits());
        }
        ComplexNumber[] matrix = operator.matrix();
        ComplexNumber m00 = matrix[0];
        ComplexNumber m01 = matrix[1];
        ComplexNumber m10 = matrix[2];
        ComplexNumber m11 = matrix[3];
        StatevectorKernels.applySingleGate(state, target,
                m00.real(), m00.imag(),
                m01.real(), m01.imag(),
                m10.real(), m10.imag(),
                m11.real(), m11.imag());
        renormalizeState(state);
    }

    private void applyMultiQubitKraus(double[] state, int[] targets, KrausOperator operator) {
        if (operator.numQubits() != targets.length) {
            throw new UnsupportedOperationException(
                    "Kraus operator qubit count mismatch: operator="
                            + operator.numQubits() + ", targets=" + targets.length);
        }
        int localDimension = 1 << targets.length;
        int[] offsets = new int[localDimension];
        int targetMask = 0;
        for (int localIndex = 0; localIndex < localDimension; localIndex++) {
            int offset = 0;
            for (int bit = 0; bit < targets.length; bit++) {
                if (((localIndex >>> bit) & 1) != 0) {
                    int mask = 1 << targets[bit];
                    offset |= mask;
                    targetMask |= mask;
                }
            }
            offsets[localIndex] = offset;
        }

        ComplexNumber[] matrix = operator.matrix();
        double[] input = new double[localDimension << 1];
        double[] output = new double[localDimension << 1];
        int dimension = state.length >> 1;

        for (int basis = 0; basis < dimension; basis++) {
            if ((basis & targetMask) != 0) {
                continue;
            }
            for (int col = 0; col < localDimension; col++) {
                int sourceIndex = (basis | offsets[col]) << 1;
                int sourceBase = col << 1;
                input[sourceBase] = state[sourceIndex];
                input[sourceBase + 1] = state[sourceIndex + 1];
            }
            for (int row = 0; row < localDimension; row++) {
                double sumReal = 0.0;
                double sumImag = 0.0;
                int matrixRow = row * localDimension;
                for (int col = 0; col < localDimension; col++) {
                    ComplexNumber coeff = matrix[matrixRow + col];
                    int sourceBase = col << 1;
                    double ampReal = input[sourceBase];
                    double ampImag = input[sourceBase + 1];
                    sumReal += coeff.real() * ampReal - coeff.imag() * ampImag;
                    sumImag += coeff.real() * ampImag + coeff.imag() * ampReal;
                }
                int targetBase = row << 1;
                output[targetBase] = sumReal;
                output[targetBase + 1] = sumImag;
            }
            for (int row = 0; row < localDimension; row++) {
                int destinationIndex = (basis | offsets[row]) << 1;
                int sourceBase = row << 1;
                state[destinationIndex] = output[sourceBase];
                state[destinationIndex + 1] = output[sourceBase + 1];
            }
        }
        renormalizeState(state);
    }

    private static void renormalizeState(double[] state) {
        double normSq = ComplexArrays.norm2Sq(state);
        if (!Double.isFinite(normSq) || normSq == 0.0) {
            throw new IllegalStateException(
                    "state collapsed to invalid norm after noise application");
        }
        if (Math.abs(normSq - 1.0) > 1e-12) {
            double scale = 1.0 / Math.sqrt(normSq);
            ComplexArrays.scal(state, 0, state.length >> 1, scale, 0.0);
        }
    }
}
