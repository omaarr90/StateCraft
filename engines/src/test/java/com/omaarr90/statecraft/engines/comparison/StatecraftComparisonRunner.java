package com.omaarr90.statecraft.engines.comparison;

import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.core.noise.ErrorChannel;
import com.omaarr90.statecraft.core.noise.NoiseModel;
import com.omaarr90.statecraft.core.parse.CircuitFormat;
import com.omaarr90.statecraft.core.parse.CircuitParsers;
import com.omaarr90.statecraft.engines.statevector.StatevectorEngine;
import com.omaarr90.statecraft.engines.statevector.StatevectorExecutionConfig;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * CLI runner used by the external benchmark harness.
 */
public final class StatecraftComparisonRunner {

	private StatecraftComparisonRunner() {
	}

	public static void main(String[] args) throws Exception {
		Options options = Options.parse(args);
		QuantumCircuit circuit = CircuitParsers.parse(options.circuitJson, CircuitFormat.JSON);
		StatevectorEngine engine = new StatevectorEngine(
				StatevectorExecutionConfig.withParallelism(options.parallelism));
		NoiseModel noiseModel = options.noiseType == null ? null : buildNoiseModel(options);

		for (int i = 0; i < options.warmupRuns; i++) {
			runOnce(engine, circuit, noiseModel, options.noiseSeed + i);
		}

		List<Double> timedRunsMs = new ArrayList<>();
		StateVector finalState = null;
		double[] probabilities = null;
		if (noiseModel == null) {
			for (int i = 0; i < options.timedRuns; i++) {
				long start = System.nanoTime();
				finalState = runOnce(engine, circuit, null, options.noiseSeed + i);
				timedRunsMs.add(toMillis(System.nanoTime() - start));
			}
			probabilities = probabilities(finalState);
		} else {
			double[] aggregateProbabilities = null;
			for (int run = 0; run < options.timedRuns; run++) {
				long start = System.nanoTime();
				aggregateProbabilities = aggregateNoiseProbabilities(engine, circuit, noiseModel, options);
				timedRunsMs.add(toMillis(System.nanoTime() - start));
			}
			probabilities = aggregateProbabilities;
		}

		System.out.println(renderResult(options, circuit, timedRunsMs, finalState, probabilities));
	}

	private static StateVector runOnce(StatevectorEngine engine, QuantumCircuit circuit, NoiseModel noiseModel,
			long seed) {
		SimulationRequest request = SimulationRequest.zeroState(circuit);
		if (noiseModel != null) {
			request = request.withNoiseModel(noiseModel).withNoiseSeed(seed);
		}
		return engine.simulate(request).finalState().orElseThrow();
	}

	private static double[] aggregateNoiseProbabilities(StatevectorEngine engine, QuantumCircuit circuit,
			NoiseModel noiseModel, Options options) {
		int dimension = 1 << circuit.qubitCount();
		double[] aggregate = new double[dimension];
		for (int trajectory = 0; trajectory < options.noiseTrajectories; trajectory++) {
			StateVector state = runOnce(engine, circuit, noiseModel, options.noiseSeed + trajectory);
			double[] probabilities = probabilities(state);
			for (int index = 0; index < aggregate.length; index++) {
				aggregate[index] += probabilities[index];
			}
		}
		double scale = 1.0 / options.noiseTrajectories;
		for (int index = 0; index < aggregate.length; index++) {
			aggregate[index] *= scale;
		}
		return aggregate;
	}

	private static NoiseModel buildNoiseModel(Options options) {
		NoiseModel.Builder builder = NoiseModel.builder();
		for (int qubit : options.noiseQubits) {
			builder.afterAllGates(createChannel(options, qubit));
		}
		return builder.build();
	}

	private static ErrorChannel createChannel(Options options, int qubit) {
		return switch (options.noiseType) {
			case "depolarizing" -> ErrorChannel.depolarizing(options.noiseProbability, qubit);
			case "phase_flip" -> ErrorChannel.phaseFlip(options.noiseProbability, qubit);
			case "amplitude_damping" -> ErrorChannel.amplitudeDamping(options.noiseGamma, qubit);
			case "phase_damping" -> ErrorChannel.phaseDamping(options.noiseLambda, qubit);
			case "thermal_relaxation" ->
				ErrorChannel.thermalRelaxation(options.noiseT1, options.noiseT2, options.noiseGateTime, qubit);
			default -> throw new IllegalArgumentException("unknown noise type: " + options.noiseType);
		};
	}

	private static double[] probabilities(StateVector state) {
		double[] data = state.data();
		double[] probabilities = new double[state.dimension()];
		for (int index = 0; index < probabilities.length; index++) {
			double real = data[index << 1];
			double imag = data[(index << 1) + 1];
			probabilities[index] = real * real + imag * imag;
		}
		return probabilities;
	}

	private static String renderResult(Options options, QuantumCircuit circuit, List<Double> timedRunsMs,
			StateVector finalState, double[] probabilities) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		appendField(sb, "runner", "statecraft").append(',');
		appendField(sb, "fixture_id", options.fixtureId).append(',');
		appendField(sb, "category", options.category).append(',');
		appendField(sb, "status", "ok").append(',');
		sb.append("\"qubits\":").append(circuit.qubitCount()).append(',');
		sb.append("\"operations\":")
				.append(circuit.operations().stream()
						.filter(operation -> !(operation instanceof QuantumCircuit.Operation.MeasureOperation)).count())
				.append(',');
		sb.append("\"threads\":").append(options.parallelism).append(',');
		sb.append("\"warmup_runs\":").append(options.warmupRuns).append(',');
		sb.append("\"timed_runs\":").append(options.timedRuns).append(',');
		sb.append("\"noise_trajectories\":").append(options.noiseType == null ? 0 : options.noiseTrajectories)
				.append(',');
		sb.append("\"timed_runs_ms\":");
		appendDoubleArray(sb, timedRunsMs).append(',');
		sb.append("\"mean_ms\":").append(format(mean(timedRunsMs))).append(',');
		sb.append("\"probabilities\":");
		appendDoubleArray(sb, probabilities);
		if (finalState != null) {
			sb.append(",\"statevector\":");
			appendStatevector(sb, finalState);
		}
		sb.append("}");
		return sb.toString();
	}

	private static StringBuilder appendField(StringBuilder sb, String name, String value) {
		sb.append('"').append(escape(name)).append("\":\"").append(escape(value)).append('"');
		return sb;
	}

	private static StringBuilder appendDoubleArray(StringBuilder sb, List<Double> values) {
		sb.append('[');
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(format(values.get(i)));
		}
		sb.append(']');
		return sb;
	}

	private static StringBuilder appendDoubleArray(StringBuilder sb, double[] values) {
		sb.append('[');
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(format(values[i]));
		}
		sb.append(']');
		return sb;
	}

	private static void appendStatevector(StringBuilder sb, StateVector state) {
		sb.append('[');
		for (int index = 0; index < state.dimension(); index++) {
			if (index > 0) {
				sb.append(',');
			}
			ComplexNumber amplitude = state.amplitude(index);
			sb.append('[').append(format(amplitude.real())).append(',').append(format(amplitude.imag())).append(']');
		}
		sb.append(']');
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static double mean(List<Double> values) {
		return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
	}

	private static double toMillis(long nanos) {
		return nanos / 1_000_000.0;
	}

	private static String format(double value) {
		return String.format(Locale.US, "%.17g", value);
	}

	private static final class Options {

		private String fixtureId = "";
		private String category = "";
		private Path circuitJson;
		private int warmupRuns = 1;
		private int timedRuns = 5;
		private int parallelism = 1;
		private String noiseType;
		private int[] noiseQubits = new int[0];
		private int noiseTrajectories = 512;
		private long noiseSeed = 0x51A7EC0FFEEAL;
		private double noiseProbability;
		private double noiseGamma;
		private double noiseLambda;
		private double noiseT1;
		private double noiseT2;
		private double noiseGateTime;

		private static Options parse(String[] args) {
			Options options = new Options();
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				String value = switch (arg) {
					case "--fixture-id", "--category", "--circuit-json", "--warmup-runs", "--timed-runs",
							"--parallelism", "--noise-type", "--noise-qubits", "--noise-trajectories", "--noise-seed",
							"--noise-probability", "--noise-gamma", "--noise-lambda", "--noise-t1", "--noise-t2",
							"--noise-gate-time" -> {
						if (i + 1 >= args.length) {
							throw new IllegalArgumentException("missing value for " + arg);
						}
						yield args[++i];
					}
					default -> throw new IllegalArgumentException("unknown argument: " + arg);
				};
				options.apply(arg, value);
			}
			if (options.circuitJson == null) {
				throw new IllegalArgumentException("--circuit-json is required");
			}
			if (options.noiseType != null && options.noiseQubits.length == 0) {
				throw new IllegalArgumentException("--noise-qubits is required when --noise-type is provided");
			}
			return options;
		}

		private void apply(String arg, String value) {
			switch (arg) {
				case "--fixture-id" -> fixtureId = value;
				case "--category" -> category = value;
				case "--circuit-json" -> circuitJson = Path.of(value);
				case "--warmup-runs" -> warmupRuns = Integer.parseInt(value);
				case "--timed-runs" -> timedRuns = Integer.parseInt(value);
				case "--parallelism" -> parallelism = Integer.parseInt(value);
				case "--noise-type" -> noiseType = value;
				case "--noise-qubits" -> noiseQubits = parseIntList(value);
				case "--noise-trajectories" -> noiseTrajectories = Integer.parseInt(value);
				case "--noise-seed" -> noiseSeed = Long.parseLong(value);
				case "--noise-probability" -> noiseProbability = Double.parseDouble(value);
				case "--noise-gamma" -> noiseGamma = Double.parseDouble(value);
				case "--noise-lambda" -> noiseLambda = Double.parseDouble(value);
				case "--noise-t1" -> noiseT1 = Double.parseDouble(value);
				case "--noise-t2" -> noiseT2 = Double.parseDouble(value);
				case "--noise-gate-time" -> noiseGateTime = Double.parseDouble(value);
				default -> throw new IllegalArgumentException("unknown argument: " + arg);
			}
		}

		private static int[] parseIntList(String value) {
			if (value.isBlank()) {
				return new int[0];
			}
			return Arrays.stream(value.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
		}
	}
}
