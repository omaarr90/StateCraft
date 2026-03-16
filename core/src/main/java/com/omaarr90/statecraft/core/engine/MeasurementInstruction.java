package com.omaarr90.statecraft.core.engine;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Configuration describing how a simulation should perform measurement
 * sampling.
 */
public record MeasurementInstruction(int shots, Optional<int[]> measuredQubits, OptionalLong seed, Mode mode) {

	public MeasurementInstruction {
		if (shots <= 0) {
			throw new IllegalArgumentException("shots must be positive");
		}
		measuredQubits = measuredQubits == null
				? Optional.empty()
				: measuredQubits.map(MeasurementInstruction::normalizeQubits);
		seed = seed == null ? OptionalLong.empty() : seed;
		mode = Objects.requireNonNull(mode, "mode");
	}

	public Optional<int[]> measuredQubits() {
		return measuredQubits.map(int[]::clone);
	}

	public MeasurementInstruction withSeed(long newSeed) {
		return new MeasurementInstruction(shots, measuredQubits(), OptionalLong.of(newSeed), mode);
	}

	public MeasurementInstruction withoutSeed() {
		return new MeasurementInstruction(shots, measuredQubits(), OptionalLong.empty(), mode);
	}

	public static MeasurementInstruction countsAll(int shots) {
		return new MeasurementInstruction(shots, Optional.empty(), OptionalLong.empty(), Mode.COUNTS);
	}

	public static MeasurementInstruction counts(int shots, int... qubits) {
		return new MeasurementInstruction(shots, wrapQubits(qubits, true), OptionalLong.empty(), Mode.COUNTS);
	}

	public static MeasurementInstruction samplesAll(int shots) {
		return new MeasurementInstruction(shots, Optional.empty(), OptionalLong.empty(), Mode.SAMPLES);
	}

	public static MeasurementInstruction samples(int shots, int... qubits) {
		return new MeasurementInstruction(shots, wrapQubits(qubits, true), OptionalLong.empty(), Mode.SAMPLES);
	}

	private static Optional<int[]> wrapQubits(int[] qubits, boolean requireNonEmpty) {
		Objects.requireNonNull(qubits, "qubits");
		if (qubits.length == 0) {
			if (requireNonEmpty) {
				throw new IllegalArgumentException("at least one qubit must be provided");
			}
			return Optional.empty();
		}
		return Optional.of(qubits.clone());
	}

	private static int[] normalizeQubits(int[] qubits) {
		if (qubits.length == 0) {
			throw new IllegalArgumentException("measured qubit list must not be empty");
		}
		int[] copy = qubits.clone();
		for (int qubit : copy) {
			if (qubit < 0) {
				throw new IllegalArgumentException("qubit index must be non-negative: " + qubit);
			}
		}
		Arrays.sort(copy);
		for (int index = 1; index < copy.length; index++) {
			if (copy[index] == copy[index - 1]) {
				throw new IllegalArgumentException("duplicate qubit index: " + copy[index]);
			}
		}
		return copy;
	}

	public enum Mode {
		COUNTS, SAMPLES
	}
}
