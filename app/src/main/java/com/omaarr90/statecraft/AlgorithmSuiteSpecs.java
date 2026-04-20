package com.omaarr90.statecraft;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.List;

final class AlgorithmSuiteSpecs {

	private static final int DEFAULT_SHOTS = 4_096;

	private AlgorithmSuiteSpecs() {
	}

	static List<AlgorithmSpec> all() {
		return List.of(bellPair(), ghzTriplet(), deutschJozsaConstant(), deutschJozsaBalanced(), bernsteinVazirani(),
				qftOnBasisState(), quantumPhaseEstimation());
	}

	private static AlgorithmSpec bellPair() {
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1);
		MeasurementInstruction measurement = MeasurementInstruction.countsAll(DEFAULT_SHOTS).withSeed(0xBEEFL);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement);
		return new AlgorithmSpec("Bell Pair", "Generates a maximally entangled two-qubit Bell state.",
				"Shot histogram shows only 00 and 11.", circuit, request);
	}

	private static AlgorithmSpec ghzTriplet() {
		QuantumCircuit circuit = new QuantumCircuit(3).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1)
				.append(CnotGate.of(), 0, 2);
		MeasurementInstruction measurement = MeasurementInstruction.countsAll(DEFAULT_SHOTS).withSeed(0xFACE5EEDL);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement);
		return new AlgorithmSpec("GHZ State", "Prepares the three-qubit Greenberger-Horne-Zeilinger entangled state.",
				"Shot histogram shows only 000 and 111.", circuit, request);
	}

	private static AlgorithmSpec deutschJozsaConstant() {
		int inputQubits = 3;
		int ancilla = inputQubits;
		QuantumCircuit circuit = new QuantumCircuit(inputQubits + 1).append(new PauliX(), ancilla);

		for (int qubit = 0; qubit < inputQubits; qubit++) {
			circuit = circuit.append(new Hadamard(), qubit);
		}
		circuit = circuit.append(new Hadamard(), ancilla);
		for (int qubit = 0; qubit < inputQubits; qubit++) {
			circuit = circuit.append(new Hadamard(), qubit);
		}

		MeasurementInstruction measurement = MeasurementInstruction.counts(DEFAULT_SHOTS, 0, 1, 2).withSeed(0xD1C0L);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement);
		return new AlgorithmSpec("Deutsch-Jozsa (Constant)",
				"Uses a constant f(x)=0 oracle to show the all-zero witness on the input register.",
				"Shot histogram is exactly 000 on q2 q1 q0.", circuit, request);
	}

	private static AlgorithmSpec deutschJozsaBalanced() {
		int inputQubits = 3;
		int ancilla = inputQubits;
		QuantumCircuit circuit = new QuantumCircuit(inputQubits + 1).append(new PauliX(), ancilla);

		for (int qubit = 0; qubit < inputQubits; qubit++) {
			circuit = circuit.append(new Hadamard(), qubit);
		}
		circuit = circuit.append(new Hadamard(), ancilla).append(CnotGate.of(), 0, ancilla);
		for (int qubit = 0; qubit < inputQubits; qubit++) {
			circuit = circuit.append(new Hadamard(), qubit);
		}

		MeasurementInstruction measurement = MeasurementInstruction.counts(DEFAULT_SHOTS, 0, 1, 2).withSeed(0xD1BA11L);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement);
		return new AlgorithmSpec("Deutsch-Jozsa (Balanced)",
				"Uses a balanced f(x)=x0 oracle to produce a non-zero witness on the input register.",
				"Shot histogram is exactly 001 on q2 q1 q0.", circuit, request);
	}

	private static AlgorithmSpec bernsteinVazirani() {
		int inputQubits = 4;
		int ancilla = inputQubits;
		int secret = 0b1011;
		QuantumCircuit circuit = new QuantumCircuit(inputQubits + 1).append(new PauliX(), ancilla);

		for (int qubit = 0; qubit < inputQubits; qubit++) {
			circuit = circuit.append(new Hadamard(), qubit);
		}
		circuit = circuit.append(new Hadamard(), ancilla);
		for (int qubit = 0; qubit < inputQubits; qubit++) {
			if (((secret >> qubit) & 1) == 1) {
				circuit = circuit.append(CnotGate.of(), qubit, ancilla);
			}
		}
		for (int qubit = 0; qubit < inputQubits; qubit++) {
			circuit = circuit.append(new Hadamard(), qubit);
		}

		MeasurementInstruction measurement = MeasurementInstruction.counts(DEFAULT_SHOTS, 0, 1, 2, 3)
				.withSeed(0xBEEA51L);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement);
		return new AlgorithmSpec("Bernstein-Vazirani",
				"Recovers the hidden 4-bit string from a phase oracle in a single query.",
				"Shot histogram is exactly 1011 on q3 q2 q1 q0.", circuit, request);
	}

	private static AlgorithmSpec qftOnBasisState() {
		int qubits = 3;
		QuantumCircuit circuit = buildQftCircuit(qubits);
		StateVector initial = basisState(qubits, 0b101);
		MeasurementInstruction measurement = MeasurementInstruction.countsAll(DEFAULT_SHOTS).withSeed(0xF0F0F0F0L);
		SimulationRequest request = SimulationRequest.withInitialState(circuit, initial).withMeasurement(measurement);
		return new AlgorithmSpec("Quantum Fourier Transform (3-qubit)",
				"Applies the QFT to the computational basis state |101>, exposing the frequency-domain amplitudes.",
				"Final amplitudes show the expected phase-distributed transform of |101>.", circuit, request);
	}

	private static AlgorithmSpec quantumPhaseEstimation() {
		int counting = 0;
		int target = 1;
		QuantumCircuit circuit = new QuantumCircuit(2).append(new PauliX(), target).append(new Hadamard(), counting)
				.appendControlledPhase(Math.PI, counting, target).append(new Hadamard(), counting);
		MeasurementInstruction measurement = MeasurementInstruction.counts(DEFAULT_SHOTS, counting).withSeed(0x0FEE1L);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement);
		return new AlgorithmSpec("Quantum Phase Estimation (1-bit)",
				"Estimates the eigenphase of Z acting on |1> using a single counting qubit.",
				"Shot histogram is exactly 1 on q0.", circuit, request);
	}

	private static QuantumCircuit buildQftCircuit(int qubits) {
		QuantumCircuit circuit = new QuantumCircuit(qubits);
		for (int target = 0; target < qubits; target++) {
			circuit = circuit.append(new Hadamard(), target);
			for (int control = target + 1; control < qubits; control++) {
				double angle = Math.PI / (1 << (control - target));
				circuit = circuit.appendControlledPhase(angle, control, target);
			}
		}
		for (int i = 0; i < qubits / 2; i++) {
			circuit = circuit.appendSwap(i, qubits - i - 1);
		}
		return circuit;
	}

	private static StateVector basisState(int qubits, int index) {
		int dimension = 1 << qubits;
		if (index < 0 || index >= dimension) {
			throw new IllegalArgumentException("basis index out of range for " + qubits + " qubits: " + index);
		}
		double[] data = new double[dimension << 1];
		data[index << 1] = 1.0;
		return StateVector.fromArray(qubits, data);
	}
}

record AlgorithmSpec(String name, String description, String expectedOutcome, QuantumCircuit circuit,
		SimulationRequest request) {
}
