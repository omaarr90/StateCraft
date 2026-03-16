package com.omaarr90.statecraft.core.engine;

import com.omaarr90.statecraft.core.noise.NoiseModel;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Request payload for running a simulator engine against a quantum circuit.
 * Encapsulates the circuit, optional initial state vector, optional measurement
 * instruction, optional noise configuration, and whether to return the final
 * state. If no state is provided, the engine must assume the |0...0⟩ basis
 * state.
 */
public record SimulationRequest(QuantumCircuit circuit, Optional<StateVector> initialState,
		Optional<int[]> basisStateQubits, Optional<MeasurementInstruction> measurement, Optional<NoiseModel> noiseModel,
		OptionalLong noiseSeed, boolean returnFinalState) {

	public SimulationRequest {
		Objects.requireNonNull(circuit, "circuit");
		initialState = initialState == null ? Optional.empty() : initialState;
		initialState.ifPresent(state -> {
			Objects.requireNonNull(state, "initialState");
			if (state.qubitCount() != circuit.qubitCount()) {
				throw new IllegalArgumentException("Initial state qubit count " + state.qubitCount()
						+ " does not match circuit qubit count " + circuit.qubitCount());
			}
		});
		basisStateQubits = basisStateQubits == null
				? Optional.empty()
				: basisStateQubits.map(qubits -> normalizeBasisStateQubits(circuit.qubitCount(), qubits));
		if (initialState.isPresent() && basisStateQubits.isPresent()) {
			throw new IllegalArgumentException("initialState and basisStateQubits are mutually exclusive");
		}
		measurement = measurement == null ? Optional.empty() : measurement;
		measurement.ifPresent(instruction -> {
			Objects.requireNonNull(instruction, "measurement");
			instruction.measuredQubits().ifPresent(qubits -> {
				if (qubits.length == 0) {
					throw new IllegalArgumentException("measurement qubit list must not be empty");
				}
				for (int qubit : qubits) {
					if (qubit < 0 || qubit >= circuit.qubitCount()) {
						throw new IllegalArgumentException("measurement qubit out of range: " + qubit);
					}
				}
			});
		});
		noiseModel = noiseModel == null ? Optional.empty() : noiseModel;
		noiseModel.ifPresent(model -> Objects.requireNonNull(model, "noiseModel"));
		noiseSeed = noiseSeed == null ? OptionalLong.empty() : noiseSeed;
		if (!returnFinalState && measurement.isEmpty()) {
			throw new IllegalArgumentException(
					"simulation must request the final state or provide a measurement instruction");
		}
	}

	public static SimulationRequest zeroState(QuantumCircuit circuit) {
		return new SimulationRequest(circuit, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				OptionalLong.empty(), true);
	}

	public static SimulationRequest withInitialState(QuantumCircuit circuit, StateVector state) {
		Objects.requireNonNull(state, "state");
		return new SimulationRequest(circuit, Optional.of(state), Optional.empty(), Optional.empty(), Optional.empty(),
				OptionalLong.empty(), true);
	}

	public SimulationRequest withMeasurement(MeasurementInstruction instruction) {
		Objects.requireNonNull(instruction, "instruction");
		return new SimulationRequest(circuit, initialState, basisStateQubits, Optional.of(instruction), noiseModel,
				noiseSeed, returnFinalState);
	}

	public SimulationRequest withMeasurement(MeasurementInstruction instruction, boolean includeFinalState) {
		Objects.requireNonNull(instruction, "instruction");
		return new SimulationRequest(circuit, initialState, basisStateQubits, Optional.of(instruction), noiseModel,
				noiseSeed, includeFinalState);
	}

	public SimulationRequest withoutFinalState() {
		if (measurement.isEmpty()) {
			throw new IllegalStateException("cannot drop final state without measurement instruction");
		}
		if (!returnFinalState) {
			return this;
		}
		return new SimulationRequest(circuit, initialState, basisStateQubits, measurement, noiseModel, noiseSeed,
				false);
	}

	public SimulationRequest withNoiseModel(NoiseModel model) {
		Objects.requireNonNull(model, "model");
		return new SimulationRequest(circuit, initialState, basisStateQubits, measurement, Optional.of(model),
				noiseSeed, returnFinalState);
	}

	public SimulationRequest withNoiseSeed(long seed) {
		return new SimulationRequest(circuit, initialState, basisStateQubits, measurement, noiseModel,
				OptionalLong.of(seed), returnFinalState);
	}

	public SimulationRequest withBasisState(int... qubits) {
		Objects.requireNonNull(qubits, "qubits");
		return new SimulationRequest(circuit, Optional.empty(), Optional.of(qubits.clone()), measurement, noiseModel,
				noiseSeed, returnFinalState);
	}

	@Override
	public Optional<int[]> basisStateQubits() {
		return basisStateQubits.map(int[]::clone);
	}

	private static int[] normalizeBasisStateQubits(int qubitCount, int[] qubits) {
		int[] copy = qubits.clone();
		for (int qubit : copy) {
			if (qubit < 0 || qubit >= qubitCount) {
				throw new IllegalArgumentException("basis-state qubit out of range: " + qubit);
			}
		}
		java.util.Arrays.sort(copy);
		for (int index = 1; index < copy.length; index++) {
			if (copy[index] == copy[index - 1]) {
				throw new IllegalArgumentException("duplicate basis-state qubit: " + copy[index]);
			}
		}
		return copy;
	}
}
