package com.omaarr90.statecraft;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.core.noise.ErrorChannel;
import com.omaarr90.statecraft.core.noise.NoiseModel;
import com.omaarr90.statecraft.engines.stabilizer.StabilizerEngine;
import com.omaarr90.statecraft.engines.statevector.StatevectorEngine;
import com.omaarr90.statecraft.engines.tensornetwork.TensorNetworkEngine;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.MatrixSingleQubitGate;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.PauliZ;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.List;
import java.util.Map;

final class AlgorithmSuiteSpecs {

	private static final int DEFAULT_SHOTS = 4_096;
	private static final int WIDE_SHOTS = 128;
	private static final int NOISY_SHOTS = 1_024;
	private static final EngineExpectation SUPPORTED = EngineExpectation.supportedEngine();

	private AlgorithmSuiteSpecs() {
	}

	static List<ExampleSpec> all() {
		return List.of(bellPair(), ghzTriplet(), deutschJozsaConstant(), deutschJozsaBalanced(), bernsteinVazirani(),
				qftOnBasisState(), quantumPhaseEstimation(), groverThreeQubitSearch(), qftPhaseGradientSixQubit(),
				qaoaRingAnsatz(), largeLineClusterSampling(), tensorDepthLimitCircuit(), noisyGhzSampling());
	}

	private static ExampleSpec bellPair() {
		QuantumCircuit circuit = new QuantumCircuit(2).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1);
		MeasurementInstruction measurement = MeasurementInstruction.countsAll(DEFAULT_SHOTS).withSeed(0xBEEFL);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement);
		return new ExampleSpec("Bell Pair", "Textbook baseline",
				"Generates a maximally entangled two-qubit Bell state.", "Shot histogram shows only 00 and 11.",
				circuit, request, supportedByAll());
	}

	private static ExampleSpec ghzTriplet() {
		QuantumCircuit circuit = new QuantumCircuit(3).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1)
				.append(CnotGate.of(), 0, 2);
		MeasurementInstruction measurement = MeasurementInstruction.countsAll(DEFAULT_SHOTS).withSeed(0xFACE5EEDL);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement);
		return new ExampleSpec("GHZ State", "Textbook baseline",
				"Prepares the three-qubit Greenberger-Horne-Zeilinger entangled state.",
				"Shot histogram shows only 000 and 111.", circuit, request, supportedByAll());
	}

	private static ExampleSpec deutschJozsaConstant() {
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
		return new ExampleSpec("Deutsch-Jozsa (Constant)", "Textbook baseline",
				"Uses a constant f(x)=0 oracle to show the all-zero witness on the input register.",
				"Shot histogram is exactly 000 on q2 q1 q0.", circuit, request, supportedByAll());
	}

	private static ExampleSpec deutschJozsaBalanced() {
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
		return new ExampleSpec("Deutsch-Jozsa (Balanced)", "Textbook baseline",
				"Uses a balanced f(x)=x0 oracle to produce a non-zero witness on the input register.",
				"Shot histogram is exactly 001 on q2 q1 q0.", circuit, request, supportedByAll());
	}

	private static ExampleSpec bernsteinVazirani() {
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
		return new ExampleSpec("Bernstein-Vazirani", "Textbook baseline",
				"Recovers the hidden 4-bit string from a phase oracle in a single query.",
				"Shot histogram is exactly 1011 on q3 q2 q1 q0.", circuit, request, supportedByAll());
	}

	private static ExampleSpec qftOnBasisState() {
		int qubits = 3;
		QuantumCircuit circuit = buildQftCircuit(qubits);
		StateVector initial = basisState(qubits, 0b101);
		MeasurementInstruction measurement = MeasurementInstruction.countsAll(DEFAULT_SHOTS).withSeed(0xF0F0F0F0L);
		SimulationRequest request = SimulationRequest.withInitialState(circuit, initial).withMeasurement(measurement);
		return new ExampleSpec("Quantum Fourier Transform (3-qubit)", "Textbook baseline",
				"Applies the QFT to the computational basis state |101>, exposing frequency-domain amplitudes.",
				"Final amplitudes show the expected phase-distributed transform of |101>.", circuit, request,
				statevectorAndTensorOnly("non-Clifford controlled-phase rotations are outside the stabilizer tableau"));
	}

	private static ExampleSpec quantumPhaseEstimation() {
		int counting = 0;
		int target = 1;
		QuantumCircuit circuit = new QuantumCircuit(2).append(new PauliX(), target).append(new Hadamard(), counting)
				.appendControlledPhase(Math.PI, counting, target).append(new Hadamard(), counting);
		MeasurementInstruction measurement = MeasurementInstruction.counts(DEFAULT_SHOTS, counting).withSeed(0x0FEE1L);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement);
		return new ExampleSpec("Quantum Phase Estimation (1-bit)", "Textbook baseline",
				"Estimates the eigenphase of Z acting on |1> using a single counting qubit.",
				"Shot histogram is exactly 1 on q0.", circuit, request, supportedByAll());
	}

	private static ExampleSpec groverThreeQubitSearch() {
		QuantumCircuit circuit = new QuantumCircuit(3);
		for (int qubit = 0; qubit < 3; qubit++) {
			circuit = circuit.append(new Hadamard(), qubit);
		}
		circuit = circuit.append(new PauliX(), 1).appendMultiControl(new PauliZ(), 2, 0, 1).append(new PauliX(), 1);
		for (int qubit = 0; qubit < 3; qubit++) {
			circuit = circuit.append(new Hadamard(), qubit).append(new PauliX(), qubit);
		}
		circuit = circuit.appendMultiControl(new PauliZ(), 2, 0, 1);
		for (int qubit = 0; qubit < 3; qubit++) {
			circuit = circuit.append(new PauliX(), qubit).append(new Hadamard(), qubit);
		}

		MeasurementInstruction measurement = MeasurementInstruction.countsAll(DEFAULT_SHOTS).withSeed(0x6700EAL);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement);
		return new ExampleSpec("Grover Search (3-qubit marked state)", "Engine limit",
				"Runs one Grover iteration for the marked state 101 using two-control phase reflections.",
				"Statevector amplifies the marked state; tableau and MPS engines reject the multi-control phase.",
				circuit, request,
				Map.of(StatevectorEngine.ID, SUPPORTED, StabilizerEngine.ID, EngineExpectation.unsupported(
						"multi-control phase uses two controls; stabilizer supports single-control Pauli gates only"),
						TensorNetworkEngine.ID,
						EngineExpectation.unsupported("tensornetwork engine v1 does not support multi-control gates")));
	}

	private static ExampleSpec qftPhaseGradientSixQubit() {
		int qubits = 6;
		QuantumCircuit circuit = buildQftCircuit(qubits);
		StateVector initial = basisState(qubits, 0b101101);
		MeasurementInstruction measurement = MeasurementInstruction.counts(DEFAULT_SHOTS, 0, 1, 2, 3).withSeed(0x6F7L);
		SimulationRequest request = SimulationRequest.withInitialState(circuit, initial).withMeasurement(measurement);
		return new ExampleSpec("QFT Phase Gradient (6-qubit)", "Realistic algorithm",
				"Applies a six-qubit QFT to a non-trivial basis state, producing dense phase structure.",
				"Statevector and MPS handle shallow non-Clifford phase rotations; stabilizer rejects them.", circuit,
				request,
				statevectorAndTensorOnly("non-Clifford controlled-phase rotations are outside the stabilizer tableau"));
	}

	private static ExampleSpec qaoaRingAnsatz() {
		int qubits = 6;
		double gamma = 0.42;
		double beta = 0.55;
		QuantumCircuit circuit = new QuantumCircuit(qubits);
		for (int qubit = 0; qubit < qubits; qubit++) {
			circuit = circuit.append(new Hadamard(), qubit);
		}
		for (int qubit = 0; qubit < qubits; qubit++) {
			circuit = circuit.appendDiagonalTwoQubit(zzPhase(gamma), qubit, (qubit + 1) % qubits);
		}
		for (int qubit = 0; qubit < qubits; qubit++) {
			circuit = circuit.append(rx(2.0 * beta), qubit);
		}

		MeasurementInstruction measurement = MeasurementInstruction.countsAll(DEFAULT_SHOTS).withSeed(0x0A0AL);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement, false);
		return new ExampleSpec("QAOA Ring Ansatz (6-qubit)", "Realistic workload",
				"Runs one MaxCut-style QAOA layer with ZZ cost phases and Rx mixer rotations.",
				"Statevector and MPS support the variational gates; stabilizer rejects the non-Clifford rotations.",
				circuit, request, statevectorAndTensorOnly(
						"non-Clifford rotations and arbitrary ZZ phases are outside the stabilizer tableau"));
	}

	private static ExampleSpec largeLineClusterSampling() {
		int qubits = 40;
		QuantumCircuit circuit = buildLineClusterCircuit(qubits);
		MeasurementInstruction measurement = MeasurementInstruction.counts(WIDE_SHOTS, 0, 1, 2, 3, 4, 5, 6, 7)
				.withSeed(0xC1057EAL);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement, false);
		return new ExampleSpec("Line Cluster Sampling (40-qubit)", "Engine limit",
				"Samples an open-chain Clifford cluster state above the dense statevector qubit limit.",
				"Stabilizer and MPS run shots-only; statevector rejects 40 qubits in dense mode.", circuit, request,
				Map.of(StatevectorEngine.ID,
						EngineExpectation.unsupported("40 qubits exceeds the statevector dense limit of 29"),
						StabilizerEngine.ID, SUPPORTED, TensorNetworkEngine.ID, SUPPORTED));
	}

	private static ExampleSpec tensorDepthLimitCircuit() {
		QuantumCircuit circuit = new QuantumCircuit(1);
		for (int layer = 0; layer < 41; layer++) {
			circuit = circuit.append(new Hadamard(), 0);
		}
		MeasurementInstruction measurement = MeasurementInstruction.countsAll(DEFAULT_SHOTS).withSeed(0xDE97A11L);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement);
		return new ExampleSpec("Depth Limit Probe (41 layers)", "Engine limit",
				"Applies 41 dependent Clifford layers to exceed the MPS depth guard while staying tiny.",
				"Statevector and stabilizer execute the circuit; tensornetwork rejects depth above 40.", circuit,
				request, Map.of(StatevectorEngine.ID, SUPPORTED, StabilizerEngine.ID, SUPPORTED, TensorNetworkEngine.ID,
						EngineExpectation.unsupported("estimated depth 41 exceeds the tensornetwork limit of 40")));
	}

	private static ExampleSpec noisyGhzSampling() {
		QuantumCircuit circuit = new QuantumCircuit(3).append(new Hadamard(), 0).append(CnotGate.of(), 0, 1)
				.append(CnotGate.of(), 0, 2);
		NoiseModel.Builder noise = NoiseModel.builder();
		for (int qubit = 0; qubit < 3; qubit++) {
			noise.afterAllGates(ErrorChannel.phaseFlip(0.05, qubit));
		}
		MeasurementInstruction measurement = MeasurementInstruction.countsAll(NOISY_SHOTS).withSeed(0x9015EL);
		SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(measurement, false)
				.withNoiseModel(noise.build()).withNoiseSeed(0x51A7ECL);
		return new ExampleSpec("Noisy GHZ Sampling", "Engine limit",
				"Samples a GHZ circuit with seeded phase-flip noise after each unitary gate.",
				"Only statevector executes noisy trajectories today; the other engines reject noise.", circuit, request,
				Map.of(StatevectorEngine.ID, SUPPORTED, StabilizerEngine.ID,
						EngineExpectation.unsupported("stabilizer engine does not support noisy simulation yet"),
						TensorNetworkEngine.ID,
						EngineExpectation.unsupported("tensornetwork engine does not support noisy simulation yet")));
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

	private static QuantumCircuit buildLineClusterCircuit(int qubits) {
		QuantumCircuit circuit = new QuantumCircuit(qubits);
		for (int qubit = 0; qubit < qubits; qubit++) {
			circuit = circuit.append(new Hadamard(), qubit);
		}
		for (int qubit = 0; qubit < qubits - 1; qubit++) {
			circuit = circuit.appendControlledPhase(Math.PI, qubit, qubit + 1);
		}
		return circuit;
	}

	private static ComplexNumber[] zzPhase(double gamma) {
		ComplexNumber aligned = phase(-gamma);
		ComplexNumber antiAligned = phase(gamma);
		return new ComplexNumber[]{aligned, antiAligned, antiAligned, aligned};
	}

	private static MatrixSingleQubitGate rx(double angle) {
		double half = angle / 2.0;
		double cos = Math.cos(half);
		double sin = Math.sin(half);
		return new MatrixSingleQubitGate(String.format(java.util.Locale.US, "Rx(%.3f)", angle),
				new ComplexNumber(cos, 0.0), new ComplexNumber(0.0, -sin), new ComplexNumber(0.0, -sin),
				new ComplexNumber(cos, 0.0));
	}

	private static ComplexNumber phase(double angle) {
		return new ComplexNumber(Math.cos(angle), Math.sin(angle));
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

	private static Map<String, EngineExpectation> supportedByAll() {
		return Map.of(StatevectorEngine.ID, SUPPORTED, StabilizerEngine.ID, SUPPORTED, TensorNetworkEngine.ID,
				SUPPORTED);
	}

	private static Map<String, EngineExpectation> statevectorAndTensorOnly(String stabilizerReason) {
		return Map.of(StatevectorEngine.ID, SUPPORTED, StabilizerEngine.ID,
				EngineExpectation.unsupported(stabilizerReason), TensorNetworkEngine.ID, SUPPORTED);
	}
}

record ExampleSpec(String name, String category, String description, String expectedOutcome, QuantumCircuit circuit,
		SimulationRequest request, Map<String, EngineExpectation> expectations) {

	ExampleSpec {
		expectations = Map.copyOf(expectations);
	}

	EngineExpectation expectationFor(String engineId) {
		return expectations.getOrDefault(engineId, EngineExpectation.unsupported("engine is not part of this suite"));
	}
}

record EngineExpectation(boolean supported, String reason) {

	EngineExpectation {
		reason = reason == null ? "" : reason;
		if (!supported && reason.isBlank()) {
			throw new IllegalArgumentException("unsupported engine expectations require a reason");
		}
	}

	static EngineExpectation supportedEngine() {
		return new EngineExpectation(true, "");
	}

	static EngineExpectation unsupported(String reason) {
		return new EngineExpectation(false, reason);
	}
}
