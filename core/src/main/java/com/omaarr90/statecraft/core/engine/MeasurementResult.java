package com.omaarr90.statecraft.core.engine;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result payload describing the outcome of a shot-based measurement.
 */
public sealed interface MeasurementResult permits MeasurementResult.Histogram, MeasurementResult.Samples,
		MeasurementResult.BitstringHistogram, MeasurementResult.BitstringSamples {

	int[] measuredQubits();

	int shots();

	default int measuredQubitCount() {
		return measuredQubits().length;
	}

	/**
	 * Histogram-form measurement result aggregating counts per outcome.
	 */
	record Histogram(int[] measuredQubits, int shots, Map<Integer, Integer> counts) implements MeasurementResult {

		public Histogram {
			Objects.requireNonNull(measuredQubits, "measuredQubits");
			Objects.requireNonNull(counts, "counts");
			if (shots <= 0) {
				throw new IllegalArgumentException("shots must be positive");
			}
			if (measuredQubits.length == 0) {
				throw new IllegalArgumentException("measuredQubits must not be empty");
			}
			int[] copy = measuredQubits.clone();
			for (int qubit : copy) {
				if (qubit < 0) {
					throw new IllegalArgumentException("qubit index must be non-negative");
				}
			}
			measuredQubits = copy;
			counts = Map.copyOf(counts);
			long totalShots = counts.values().stream().mapToLong(Integer::longValue).sum();
			if (totalShots != shots) {
				throw new IllegalArgumentException(
						"histogram counts sum " + totalShots + " does not match shots " + shots);
			}
		}

		@Override
		public int[] measuredQubits() {
			return measuredQubits.clone();
		}
	}

	/**
	 * Raw-sample measurement result, containing one entry per shot.
	 */
	record Samples(int[] measuredQubits, int shots, List<Integer> outcomes) implements MeasurementResult {

		public Samples {
			Objects.requireNonNull(measuredQubits, "measuredQubits");
			Objects.requireNonNull(outcomes, "outcomes");
			if (shots <= 0) {
				throw new IllegalArgumentException("shots must be positive");
			}
			if (measuredQubits.length == 0) {
				throw new IllegalArgumentException("measuredQubits must not be empty");
			}
			if (outcomes.size() != shots) {
				throw new IllegalArgumentException("expected " + shots + " samples, got " + outcomes.size());
			}
			int[] copy = measuredQubits.clone();
			for (int qubit : copy) {
				if (qubit < 0) {
					throw new IllegalArgumentException("qubit index must be non-negative");
				}
			}
			measuredQubits = copy;
			outcomes = List.copyOf(outcomes);
		}

		@Override
		public int[] measuredQubits() {
			return measuredQubits.clone();
		}
	}

	/**
	 * Histogram-form measurement result for arbitrary-width bitstrings.
	 */
	record BitstringHistogram(int[] measuredQubits, int shots,
			Map<String, Integer> counts) implements MeasurementResult {

		public BitstringHistogram {
			int[] copy = normalizeMeasuredQubits(measuredQubits);
			Objects.requireNonNull(counts, "counts");
			if (shots <= 0) {
				throw new IllegalArgumentException("shots must be positive");
			}
			counts = Map.copyOf(counts);
			long totalShots = counts.values().stream().mapToLong(Integer::longValue).sum();
			if (totalShots != shots) {
				throw new IllegalArgumentException(
						"histogram counts sum " + totalShots + " does not match shots " + shots);
			}
			for (String bitstring : counts.keySet()) {
				validateBitstring(bitstring, copy.length);
			}
			measuredQubits = copy;
		}

		@Override
		public int[] measuredQubits() {
			return measuredQubits.clone();
		}
	}

	/**
	 * Raw-sample measurement result for arbitrary-width bitstrings.
	 */
	record BitstringSamples(int[] measuredQubits, int shots, List<String> outcomes) implements MeasurementResult {

		public BitstringSamples {
			int[] copy = normalizeMeasuredQubits(measuredQubits);
			Objects.requireNonNull(outcomes, "outcomes");
			if (shots <= 0) {
				throw new IllegalArgumentException("shots must be positive");
			}
			if (outcomes.size() != shots) {
				throw new IllegalArgumentException("expected " + shots + " samples, got " + outcomes.size());
			}
			for (String outcome : outcomes) {
				validateBitstring(outcome, copy.length);
			}
			measuredQubits = copy;
			outcomes = List.copyOf(outcomes);
		}

		@Override
		public int[] measuredQubits() {
			return measuredQubits.clone();
		}
	}

	private static int[] normalizeMeasuredQubits(int[] measuredQubits) {
		Objects.requireNonNull(measuredQubits, "measuredQubits");
		if (measuredQubits.length == 0) {
			throw new IllegalArgumentException("measuredQubits must not be empty");
		}
		int[] copy = measuredQubits.clone();
		for (int qubit : copy) {
			if (qubit < 0) {
				throw new IllegalArgumentException("qubit index must be non-negative");
			}
		}
		return copy;
	}

	private static void validateBitstring(String bitstring, int width) {
		Objects.requireNonNull(bitstring, "bitstring");
		if (bitstring.length() != width) {
			throw new IllegalArgumentException(
					"bitstring length " + bitstring.length() + " does not match measured width " + width);
		}
		for (int index = 0; index < bitstring.length(); index++) {
			char bit = bitstring.charAt(index);
			if (bit != '0' && bit != '1') {
				throw new IllegalArgumentException("bitstring contains non-binary character: " + bitstring);
			}
		}
	}
}
