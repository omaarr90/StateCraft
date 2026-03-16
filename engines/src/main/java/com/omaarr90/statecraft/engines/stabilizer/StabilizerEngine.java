package com.omaarr90.statecraft.engines.stabilizer;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.MeasurementResult;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.PauliY;
import com.omaarr90.statecraft.quantum.PauliZ;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.SGate;
import com.omaarr90.statecraft.quantum.SdgGate;
import com.omaarr90.statecraft.quantum.SingleQubitGate;
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
 * Stabilizer simulator backed by an Aaronson-Gottesman tableau.
 */
public final class StabilizerEngine implements SimulatorEngine {

	public static final String ID = "stabilizer";

	static final int MAX_FINAL_STATE_QUBITS = 20;

	private static final double EPS = 1e-12;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public SimulationResult simulate(SimulationRequest request) {
		Objects.requireNonNull(request, "request");
		ensureNoiseIsUnsupported(request);

		QuantumCircuit circuit = request.circuit();
		int qubitCount = circuit.qubitCount();
		if (request.returnFinalState() && qubitCount > MAX_FINAL_STATE_QUBITS) {
			throw new UnsupportedOperationException("stabilizer engine cannot materialize amplitudes above "
					+ MAX_FINAL_STATE_QUBITS + " qubits; request shots only or use the statevector engine");
		}

		int[] basisStateQubits = resolveBasisStateQubits(request);
		StabilizerTableau tableau = new StabilizerTableau(qubitCount);
		tableau.setBasisStateQubits(basisStateQubits);

		StabilizerAmplitudeMirror mirror = request.returnFinalState()
				? createAmplitudeMirror(request, qubitCount, basisStateQubits)
				: null;

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
			applyOperation(tableau, mirror, operation);
		}

		Optional<StateVector> finalState = request.returnFinalState()
				? Optional.of(mirror.toStateVector())
				: Optional.empty();
		Optional<MeasurementResult> measurement = request.measurement()
				.map(instruction -> performMeasurement(tableau, qubitCount, instruction, measureOperations));

		if (finalState.isPresent() && measurement.isPresent()) {
			return SimulationResult.forStateAndMeasurement(finalState.get(), measurement.get());
		}
		if (finalState.isPresent()) {
			return SimulationResult.forState(finalState.get());
		}
		return SimulationResult.forMeasurement(measurement.orElseThrow(
				() -> new IllegalStateException("measurement instruction required when final state is omitted")));
	}

	private static void ensureNoiseIsUnsupported(SimulationRequest request) {
		request.noiseModel().ifPresent(model -> {
			if (model.hasNoise()) {
				throw new UnsupportedOperationException("stabilizer engine does not support noisy simulation yet");
			}
		});
	}

	private static int[] resolveBasisStateQubits(SimulationRequest request) {
		if (request.basisStateQubits().isPresent()) {
			return request.basisStateQubits().orElseThrow();
		}
		if (request.initialState().isPresent()) {
			return extractBasisStateQubits(request.initialState().orElseThrow());
		}
		return new int[0];
	}

	private static int[] extractBasisStateQubits(StateVector state) {
		double[] data = state.copyData();
		int basisIndex = -1;
		for (int index = 0; index < state.dimension(); index++) {
			int base = index << 1;
			double magnitudeSq = (data[base] * data[base]) + (data[base + 1] * data[base + 1]);
			if (magnitudeSq <= EPS) {
				continue;
			}
			if (basisIndex != -1 || Math.abs(magnitudeSq - 1.0) > EPS) {
				throw new UnsupportedOperationException(
						"stabilizer engine supports computational-basis initial states only");
			}
			basisIndex = index;
		}
		if (basisIndex < 0) {
			throw new UnsupportedOperationException(
					"stabilizer engine supports computational-basis initial states only");
		}
		int[] qubits = new int[Integer.bitCount(basisIndex)];
		int writeIndex = 0;
		for (int qubit = 0; qubit < state.qubitCount(); qubit++) {
			if (((basisIndex >>> qubit) & 1) != 0) {
				qubits[writeIndex++] = qubit;
			}
		}
		return qubits;
	}

	private static StabilizerAmplitudeMirror createAmplitudeMirror(SimulationRequest request, int qubitCount,
			int[] basisStateQubits) {
		if (request.initialState().isPresent()) {
			return StabilizerAmplitudeMirror.fromStateVector(request.initialState().orElseThrow());
		}
		if (basisStateQubits.length > 0) {
			return StabilizerAmplitudeMirror.basisState(qubitCount, basisStateQubits);
		}
		return StabilizerAmplitudeMirror.zeroState(qubitCount);
	}

	private static void applyOperation(StabilizerTableau tableau, StabilizerAmplitudeMirror mirror,
			QuantumCircuit.Operation operation) {
		if (operation instanceof QuantumCircuit.Operation.SingleGateOperation single) {
			applySingle(tableau, mirror, single.gate(), single.qubit());
			return;
		}
		if (operation instanceof QuantumCircuit.Operation.CnotOperation cnot) {
			tableau.applyCnot(cnot.controlQubit(), cnot.targetQubit());
			if (mirror != null) {
				mirror.applyCnot(cnot.controlQubit(), cnot.targetQubit());
			}
			return;
		}
		if (operation instanceof QuantumCircuit.Operation.SwapOperation swap) {
			tableau.applySwap(swap.firstQubit(), swap.secondQubit());
			if (mirror != null) {
				mirror.applySwap(swap.firstQubit(), swap.secondQubit());
			}
			return;
		}
		if (operation instanceof QuantumCircuit.Operation.TwoQubitDiagonalOperation diagonal) {
			applyDiagonal(tableau, mirror, diagonal);
			return;
		}
		if (operation instanceof QuantumCircuit.Operation.MultiControlOperation multi) {
			applyMultiControl(tableau, mirror, multi);
			return;
		}
		if (operation instanceof QuantumCircuit.Operation.TwoQubitGateOperation) {
			throw new UnsupportedOperationException("stabilizer engine does not support arbitrary two-qubit unitaries");
		}
		throw new UnsupportedOperationException(
				"stabilizer engine does not support operation type: " + operation.getClass().getName());
	}

	private static void applySingle(StabilizerTableau tableau, StabilizerAmplitudeMirror mirror, SingleQubitGate gate,
			int qubit) {
		if (gate instanceof Hadamard) {
			tableau.applyHadamard(qubit);
			if (mirror != null) {
				mirror.applyHadamard(qubit);
			}
			return;
		}
		if (gate instanceof SGate) {
			tableau.applyS(qubit);
			if (mirror != null) {
				mirror.applyS(qubit);
			}
			return;
		}
		if (gate instanceof SdgGate) {
			tableau.applySdg(qubit);
			if (mirror != null) {
				mirror.applySdg(qubit);
			}
			return;
		}
		if (gate instanceof PauliX) {
			tableau.applyX(qubit);
			if (mirror != null) {
				mirror.applyX(qubit);
			}
			return;
		}
		if (gate instanceof PauliY) {
			tableau.applyY(qubit);
			if (mirror != null) {
				mirror.applyY(qubit);
			}
			return;
		}
		if (gate instanceof PauliZ) {
			tableau.applyZ(qubit);
			if (mirror != null) {
				mirror.applyZ(qubit);
			}
			return;
		}
		throw new UnsupportedOperationException(
				"stabilizer engine supports Clifford single-qubit gates only; got " + gate.name());
	}

	private static void applyDiagonal(StabilizerTableau tableau, StabilizerAmplitudeMirror mirror,
			QuantumCircuit.Operation.TwoQubitDiagonalOperation diagonal) {
		DiagonalKind kind = classifyDiagonal(diagonal.diagonal());
		if (kind == DiagonalKind.IDENTITY) {
			return;
		}
		if (kind != DiagonalKind.CZ) {
			throw new UnsupportedOperationException(
					"stabilizer engine supports only identity/CZ-style diagonal two-qubit gates");
		}
		tableau.applyCz(diagonal.firstQubit(), diagonal.secondQubit());
		if (mirror != null) {
			mirror.applyCz(diagonal.firstQubit(), diagonal.secondQubit());
		}
	}

	private static void applyMultiControl(StabilizerTableau tableau, StabilizerAmplitudeMirror mirror,
			QuantumCircuit.Operation.MultiControlOperation operation) {
		int[] controls = operation.controlQubits();
		if (controls.length != 1) {
			throw new UnsupportedOperationException("stabilizer engine supports single-control Pauli gates only");
		}
		int control = controls[0];
		int target = operation.targetQubit();
		SingleQubitGate gate = operation.gate();
		if (gate instanceof PauliX) {
			tableau.applyCnot(control, target);
			if (mirror != null) {
				mirror.applyCnot(control, target);
			}
			return;
		}
		if (gate instanceof PauliY) {
			tableau.applySdg(target);
			tableau.applyCnot(control, target);
			tableau.applyS(target);
			if (mirror != null) {
				mirror.applySdg(target);
				mirror.applyCnot(control, target);
				mirror.applyS(target);
			}
			return;
		}
		if (gate instanceof PauliZ) {
			tableau.applyCz(control, target);
			if (mirror != null) {
				mirror.applyCz(control, target);
			}
			return;
		}
		throw new UnsupportedOperationException(
				"stabilizer engine supports controlled Pauli gates only; got controlled-" + gate.name());
	}

	private static MeasurementResult performMeasurement(StabilizerTableau tableau, int totalQubits,
			MeasurementInstruction instruction, List<QuantumCircuit.Operation.MeasureOperation> measureOperations) {
		int[] measuredQubits = resolveMeasuredQubits(totalQubits, instruction, measureOperations);
		if (measuredQubits.length == 0) {
			throw new IllegalStateException("No qubits available for measurement");
		}
		SplittableRandom rng = instruction.seed().isPresent()
				? new SplittableRandom(instruction.seed().getAsLong())
				: new SplittableRandom();
		if (measuredQubits.length <= Integer.SIZE - 1) {
			return sampleSmallMeasurement(tableau, instruction, measuredQubits, rng);
		}
		return sampleBitstringMeasurement(tableau, instruction, measuredQubits, rng);
	}

	private static MeasurementResult sampleSmallMeasurement(StabilizerTableau tableau,
			MeasurementInstruction instruction, int[] measuredQubits, SplittableRandom rng) {
		int shots = instruction.shots();
		if (instruction.mode() == MeasurementInstruction.Mode.COUNTS) {
			Map<Integer, Integer> counts = new HashMap<>();
			for (int shot = 0; shot < shots; shot++) {
				counts.merge(sampleOutcome(tableau, measuredQubits, rng), 1, Integer::sum);
			}
			return new MeasurementResult.Histogram(measuredQubits, shots, counts);
		}
		List<Integer> samples = new ArrayList<>(shots);
		for (int shot = 0; shot < shots; shot++) {
			samples.add(sampleOutcome(tableau, measuredQubits, rng));
		}
		return new MeasurementResult.Samples(measuredQubits, shots, samples);
	}

	private static MeasurementResult sampleBitstringMeasurement(StabilizerTableau tableau,
			MeasurementInstruction instruction, int[] measuredQubits, SplittableRandom rng) {
		int shots = instruction.shots();
		if (instruction.mode() == MeasurementInstruction.Mode.COUNTS) {
			Map<String, Integer> counts = new HashMap<>();
			for (int shot = 0; shot < shots; shot++) {
				counts.merge(sampleBitstringOutcome(tableau, measuredQubits, rng), 1, Integer::sum);
			}
			return new MeasurementResult.BitstringHistogram(measuredQubits, shots, counts);
		}
		List<String> samples = new ArrayList<>(shots);
		for (int shot = 0; shot < shots; shot++) {
			samples.add(sampleBitstringOutcome(tableau, measuredQubits, rng));
		}
		return new MeasurementResult.BitstringSamples(measuredQubits, shots, samples);
	}

	private static int sampleOutcome(StabilizerTableau source, int[] measuredQubits, SplittableRandom rng) {
		StabilizerTableau tableau = source.copy();
		int outcome = 0;
		for (int index = 0; index < measuredQubits.length; index++) {
			int bit = tableau.measureZ(measuredQubits[index], rng);
			if (bit == 1) {
				outcome |= 1 << index;
			}
		}
		return outcome;
	}

	private static String sampleBitstringOutcome(StabilizerTableau source, int[] measuredQubits, SplittableRandom rng) {
		StabilizerTableau tableau = source.copy();
		int[] bits = new int[measuredQubits.length];
		for (int index = 0; index < measuredQubits.length; index++) {
			bits[index] = tableau.measureZ(measuredQubits[index], rng);
		}
		StringBuilder bitstring = new StringBuilder(measuredQubits.length);
		for (int index = measuredQubits.length - 1; index >= 0; index--) {
			bitstring.append(bits[index] == 0 ? '0' : '1');
		}
		return bitstring.toString();
	}

	private static int[] resolveMeasuredQubits(int totalQubits, MeasurementInstruction instruction,
			List<QuantumCircuit.Operation.MeasureOperation> measureOperations) {
		return instruction.measuredQubits().map(int[]::clone).orElseGet(() -> {
			if (!measureOperations.isEmpty()) {
				return measureOperations.stream().flatMapToInt(operation -> Arrays.stream(operation.qubits()))
						.distinct().sorted().toArray();
			}
			int[] allQubits = new int[totalQubits];
			for (int index = 0; index < totalQubits; index++) {
				allQubits[index] = index;
			}
			return allQubits;
		});
	}

	private static DiagonalKind classifyDiagonal(ComplexNumber[] diagonal) {
		if (diagonal.length != 4) {
			return DiagonalKind.UNSUPPORTED;
		}
		boolean identity = isApprox(diagonal[0], 1.0, 0.0) && isApprox(diagonal[1], 1.0, 0.0)
				&& isApprox(diagonal[2], 1.0, 0.0) && isApprox(diagonal[3], 1.0, 0.0);
		if (identity) {
			return DiagonalKind.IDENTITY;
		}
		boolean cz = isApprox(diagonal[0], 1.0, 0.0) && isApprox(diagonal[1], 1.0, 0.0)
				&& isApprox(diagonal[2], 1.0, 0.0) && isApprox(diagonal[3], -1.0, 0.0);
		return cz ? DiagonalKind.CZ : DiagonalKind.UNSUPPORTED;
	}

	private static boolean isApprox(ComplexNumber value, double real, double imag) {
		return Math.abs(value.real() - real) <= EPS && Math.abs(value.imag() - imag) <= EPS;
	}

	private enum DiagonalKind {
		IDENTITY, CZ, UNSUPPORTED
	}
}
