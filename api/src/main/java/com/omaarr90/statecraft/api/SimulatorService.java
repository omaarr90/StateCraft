package com.omaarr90.statecraft.api;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.MeasurementResult;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.core.noise.ErrorChannel;
import com.omaarr90.statecraft.core.noise.NoiseModel;
import com.omaarr90.statecraft.core.parse.CircuitParseException;
import com.omaarr90.statecraft.core.parse.JsonCircuitParser;
import com.omaarr90.statecraft.core.parse.OpenQasmCircuitParser;
import com.omaarr90.statecraft.engines.statevector.StatevectorEngine;
import com.omaarr90.statecraft.engines.statevector.StatevectorExecutionConfig;
import com.omaarr90.statecraft.quantum.MatrixSingleQubitGate;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.SingleQubitGate;
import com.omaarr90.statecraft.quantum.StateVector;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import static com.omaarr90.statecraft.api.ApiModels.*;

/** A bounded HTTP adapter over the canonical parsers and StatevectorEngine. */
@ApplicationScoped
public class SimulatorService {
	static final int MAX_QUBITS = 6;
	static final int MAX_OPERATIONS = 256;
	static final int MAX_SHOTS = 10_000;
	private static final int MAX_SOURCE_LENGTH = 65_536;
	private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
	private static final List<String> CHANNELS = List.of("phase-flip", "depolarizing", "amplitude-damping",
			"phase-damping");
	private static final Set<String> SINGLE_GATES = Set.of("H", "X", "Y", "Z", "S", "SDG", "T", "TDG", "RX", "RY", "RZ",
			"P", "U1");
	private static final Pattern LEGACY_REGISTER = Pattern
			.compile("\\b(qreg|creg)\\s+[A-Za-z_][A-Za-z_0-9]*\\s*\\[\\s*([0-9]+)\\s*]");
	private static final Pattern MODERN_REGISTER = Pattern
			.compile("\\b(qubit|bit)\\s*(?:\\[\\s*([0-9]+)\\s*])?\\s+[A-Za-z_][A-Za-z_0-9]*\\s*;");
	private static final ObjectMapper PREFLIGHT = new ObjectMapper(
			JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
					.maxNumberLength(64).maxStringLength(MAX_SOURCE_LENGTH).build()).build());
	private final StatevectorEngine engine = new StatevectorEngine(StatevectorExecutionConfig.withParallelism(1));
	private Semaphore permits;
	@ConfigProperty(name = "statecraft.max-concurrent", defaultValue = "4")
	int maxConcurrent;

	@PostConstruct
	void initialize() {
		permits = new Semaphore(Math.clamp(maxConcurrent, 1, 16));
	}

	public Capabilities capabilities() {
		return new Capabilities("statevector", "java", MAX_QUBITS, MAX_OPERATIONS, MAX_SHOTS, false, true, CHANNELS);
	}

	public ParseResponse parse(ParseRequest request) {
		return bounded(() -> {
			require(request != null, "A request body is required.");
			QuantumCircuit circuit = parseCircuit(request.format(), request.source());
			return new ParseResponse(circuit.qubitCount(), normalize(circuit));
		});
	}

	public SimulateResponse simulate(SimulateRequest request) {
		return bounded(() -> {
			require(request != null, "A request body is required.");
			QuantumCircuit circuit = parseCircuit(request.format(), request.source());
			List<Operation> operations = normalize(circuit);
			StateVector initial = request.initialState() == null
					? null
					: validatedState(circuit.qubitCount(), request.initialState());
			validateSeed(request.seed());
			require(request.shots() == null || (request.shots() >= 1 && request.shots() <= MAX_SHOTS),
					"shots must be between 1 and " + MAX_SHOTS + ".");
			require(request.measuredQubits() == null || request.shots() != null, "measuredQubits requires shots.");
			if (request.measuredQubits() != null)
				validateMeasured(circuit.qubitCount(), request.measuredQubits());
			NoiseModel noise = request.noise() == null ? null : noiseModel(request.noise(), circuit.qubitCount());
			Long noiseSeed = request.noise() == null ? null : request.noise().seed();
			// Prefix replay uses one seed so each displayed state belongs to the same noise
			// trajectory.
			if (noise != null && noiseSeed == null)
				noiseSeed = new SplittableRandom().nextLong(MAX_SAFE_INTEGER);
			SimulationRequest simulation = simulation(circuit, initial, noise, noiseSeed);
			if (request.shots() != null) {
				MeasurementInstruction measurement = request.measuredQubits() == null
						? MeasurementInstruction.countsAll(request.shots())
						: MeasurementInstruction.counts(request.shots(), request.measuredQubits());
				if (request.seed() != null)
					measurement = measurement.withSeed(request.seed());
				simulation = simulation.withMeasurement(measurement);
			}
			SimulationResult result = engine.simulate(simulation);
			StateVector finalState = result.finalState().orElseThrow();
			List<List<Complex>> steps = new ArrayList<>();
			if (Boolean.TRUE.equals(request.includeSteps())) {
				steps.add(amplitudes(initial == null ? StateVector.zero(circuit.qubitCount()) : initial));
				for (int i = 1; i <= circuit.operations().size(); i++) {
					QuantumCircuit prefix = new QuantumCircuit(circuit.qubitCount(),
							circuit.operations().subList(0, i));
					steps.add(amplitudes(
							engine.simulate(simulation(prefix, initial, noise, noiseSeed)).finalState().orElseThrow()));
				}
			}
			Map<String, Integer> counts = null;
			int[] measuredQubits = null;
			if (result.measurement().isPresent()) {
				MeasurementResult.Histogram histogram = (MeasurementResult.Histogram) result.measurement()
						.orElseThrow();
				measuredQubits = histogram.measuredQubits();
				counts = new LinkedHashMap<>();
				int width = measuredQubits.length;
				for (Map.Entry<Integer, Integer> entry : histogram.counts().entrySet()) {
					String bits = Integer.toBinaryString(entry.getKey());
					counts.put("0".repeat(width - bits.length()) + bits, entry.getValue());
				}
			}
			return new SimulateResponse("statevector", "java", circuit.qubitCount(), operations, amplitudes(finalState),
					probabilities(finalState), steps, counts, measuredQubits);
		});
	}

	public CollapseResponse collapse(CollapseRequest request) {
		return bounded(() -> {
			require(request != null, "A request body is required.");
			int qubits = validateQubits(request.qubits());
			int target = validateTarget(request.target(), qubits);
			validateSeed(request.seed());
			StateVector state = validatedState(qubits, request.amplitudes());
			// Core measurement samples without collapse. This explicit adapter extension
			// projects a core StateVector.
			MeasurementInstruction instruction = MeasurementInstruction.counts(1, target);
			if (request.seed() != null)
				instruction = instruction.withSeed(request.seed());
			SimulationResult result = engine.simulate(
					SimulationRequest.withInitialState(new QuantumCircuit(qubits), state).withMeasurement(instruction));
			MeasurementResult.Histogram histogram = (MeasurementResult.Histogram) result.measurement().orElseThrow();
			int outcome = histogram.counts().keySet().iterator().next();
			double probability = 0;
			for (int i = 0; i < state.dimension(); i++)
				if (((i >>> target) & 1) == outcome)
					probability += state.real(i) * state.real(i) + state.imag(i) * state.imag(i);
			require(probability > 0 && Double.isFinite(probability), "Measurement produced an invalid branch.");
			double[] data = state.copyData();
			double scale = 1 / Math.sqrt(probability);
			for (int i = 0; i < state.dimension(); i++) {
				if (((i >>> target) & 1) != outcome) {
					data[i * 2] = 0;
					data[i * 2 + 1] = 0;
				} else {
					data[i * 2] *= scale;
					data[i * 2 + 1] *= scale;
				}
			}
			StateVector collapsed = StateVector.fromArray(qubits, data);
			return new CollapseResponse("statevector", "java", "api-collapse", amplitudes(collapsed),
					probabilities(collapsed), outcome, probability);
		});
	}

	public NoiseResponse noise(NoiseRequest request) {
		return bounded(() -> {
			require(request != null, "A request body is required.");
			int qubits = validateQubits(request.qubits());
			int target = validateTarget(request.target(), qubits);
			StateVector initial = validatedState(qubits, request.amplitudes());
			Noise noise = new Noise(request.channel(), request.strength(), target, request.seed());
			NoiseModel model = noiseModel(noise, qubits);
			ComplexNumber zero = ComplexNumber.zero(), one = ComplexNumber.one();
			QuantumCircuit identity = new QuantumCircuit(qubits)
					.append(new MatrixSingleQubitGate("I", one, zero, zero, one), target);
			StateVector state = engine.simulate(simulation(identity, initial, model, request.seed())).finalState()
					.orElseThrow();
			return new NoiseResponse("statevector", "java", noise.channel(), amplitudes(state), probabilities(state));
		});
	}

	private static SimulationRequest simulation(QuantumCircuit circuit, StateVector initial, NoiseModel noise,
			Long seed) {
		SimulationRequest request = initial == null
				? SimulationRequest.zeroState(circuit)
				: SimulationRequest.withInitialState(circuit, initial);
		if (noise != null)
			request = request.withNoiseModel(noise);
		if (seed != null)
			request = request.withNoiseSeed(seed);
		return request;
	}

	private QuantumCircuit parseCircuit(String format, String source) {
		require(source != null && !source.isBlank(), "source must contain a circuit.");
		require(source.length() <= MAX_SOURCE_LENGTH, "source must not exceed " + MAX_SOURCE_LENGTH + " characters.");
		require("json".equals(format) || "qasm".equals(format), "format must be json or qasm.");
		try {
			QuantumCircuit circuit;
			if ("json".equals(format)) {
				JsonNode root = PREFLIGHT.readTree(source.charAt(0) == '\uFEFF' ? source.substring(1) : source);
				require(root != null && root.isObject(), "JSON circuit must be an object.");
				JsonNode qubits = root.get("qubits");
				require(qubits != null && qubits.isIntegralNumber() && qubits.canConvertToInt(),
						"qubits must be an integer.");
				validateQubits(qubits.intValue());
				JsonNode operations = root.get("operations");
				require(operations == null || operations.isNull()
						|| (operations.isArray() && operations.size() <= MAX_OPERATIONS),
						"operations must be an array of at most " + MAX_OPERATIONS + " operations.");
				rejectOversizedIntegers(root);
				circuit = new JsonCircuitParser().parse(source);
			} else {
				preflightQasm(source);
				circuit = new OpenQasmCircuitParser().parse(source);
			}
			validateQubits(circuit.qubitCount());
			require(circuit.operations().size() <= MAX_OPERATIONS,
					"Circuit must have at most " + MAX_OPERATIONS + " operations.");
			return circuit;
		} catch (CircuitParseException exception) {
			throw ApiException.invalid(exception.getMessage());
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw ApiException.invalid("Circuit JSON is invalid or exceeds parser limits.");
		}
	}

	private static void rejectOversizedIntegers(JsonNode node) {
		if (node.isIntegralNumber())
			require(node.canConvertToInt(), "Circuit integer values must fit a 32-bit integer.");
		else if (node.isContainerNode())
			node.forEach(SimulatorService::rejectOversizedIntegers);
	}

	private static void preflightQasm(String source) {
		String plain = source.replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*|\"[^\"]*\"", " ");
		require(plain.chars().filter(c -> c == ';').count() <= MAX_OPERATIONS + 16,
				"QASM contains too many statements.");
		int depth = 0, unaryRun = 0;
		for (int i = 0; i < plain.length(); i++) {
			char ch = plain.charAt(i);
			if (ch == '+' || ch == '-')
				require(++unaryRun <= 16, "QASM unary expression nesting exceeds 16 levels.");
			else if (!Character.isWhitespace(ch))
				unaryRun = 0;
			if (ch == '(')
				require(++depth <= 32, "QASM expression nesting exceeds 32 levels.");
			if (ch == ')')
				depth--;
		}
		long qubits = 0, bits = 0;
		Matcher legacy = LEGACY_REGISTER.matcher(plain);
		while (legacy.find()) {
			int size = boundedRegisterSize(legacy.group(2));
			if (legacy.group(1).equals("qreg"))
				qubits += size;
			else
				bits += size;
		}
		Matcher modern = MODERN_REGISTER.matcher(plain);
		while (modern.find()) {
			int size = modern.group(2) == null ? 1 : boundedRegisterSize(modern.group(2));
			if (modern.group(1).equals("qubit"))
				qubits += size;
			else
				bits += size;
		}
		require(qubits <= MAX_QUBITS && bits <= MAX_QUBITS,
				"QASM registers must contain at most " + MAX_QUBITS + " quantum and classical bits.");
	}

	private static int boundedRegisterSize(String number) {
		require(number.length() <= 2, "Register size exceeds the API limit.");
		int size = Integer.parseInt(number);
		require(size >= 1 && size <= MAX_QUBITS, "Register size must be between 1 and " + MAX_QUBITS + ".");
		return size;
	}

	private static List<Operation> normalize(QuantumCircuit circuit) {
		List<Operation> normalized = new ArrayList<>();
		for (QuantumCircuit.Operation operation : circuit.operations()) {
			if (operation instanceof QuantumCircuit.Operation.SingleGateOperation single) {
				String gate = single.gate().name().toUpperCase(Locale.ROOT);
				require(SINGLE_GATES.contains(gate),
						"Gate " + gate + " is supported by the parser but not the playground adapter.");
				if (gate.equals("U1"))
					gate = "P";
				Double angle = switch (gate) {
					case "RX" ->
						2 * Math.atan2(-single.gate().element(0, 1).imag(), single.gate().element(0, 0).real());
					case "RY" -> 2 * Math.atan2(single.gate().element(1, 0).real(), single.gate().element(0, 0).real());
					case "RZ" -> 2 * Math.atan2(single.gate().element(1, 1).imag(), single.gate().element(1, 1).real());
					case "P" -> Math.atan2(single.gate().element(1, 1).imag(), single.gate().element(1, 1).real());
					default -> null;
				};
				normalized.add(new Operation(gate, single.qubit(), null, null, angle, null));
			} else if (operation instanceof QuantumCircuit.Operation.CnotOperation cnot) {
				normalized.add(new Operation("CX", cnot.targetQubit(), cnot.controlQubit(), null, null, null));
			} else if (operation instanceof QuantumCircuit.Operation.MultiControlOperation multi) {
				String base = multi.gate().name().toUpperCase(Locale.ROOT);
				require(Set.of("X", "Y", "Z").contains(base), "Unsupported controlled gate in playground adapter.");
				int[] controls = multi.controlQubits();
				String gate = controls.length == 1
						? "C" + base
						: controls.length == 2 && base.equals("X") ? "CCX" : "MC" + base;
				normalized.add(new Operation(gate, multi.targetQubit(), controls.length == 1 ? controls[0] : null,
						controls.length > 1 ? controls : null, null, null));
			} else if (operation instanceof QuantumCircuit.Operation.SwapOperation swap) {
				normalized.add(new Operation("SWAP", swap.firstQubit(), swap.secondQubit(), null, null, null));
			} else if (operation instanceof QuantumCircuit.Operation.TwoQubitDiagonalOperation diagonal) {
				ComplexNumber value = diagonal.diagonal()[3];
				normalized.add(new Operation("CP", diagonal.secondQubit(), diagonal.firstQubit(), null,
						Math.atan2(value.imag(), value.real()), null));
			} else if (operation instanceof QuantumCircuit.Operation.MeasureOperation measure) {
				normalized.add(new Operation("MEASURE", measure.qubits()[0], null, null, null, measure.qubits()));
			} else
				throw ApiException.invalid("Circuit contains an operation unsupported by the playground adapter.");
		}
		return List.copyOf(normalized);
	}

	private static NoiseModel noiseModel(Noise noise, int qubits) {
		require(noise.channel() != null && CHANNELS.contains(noise.channel()),
				"noise channel must be one of " + CHANNELS + ".");
		require(noise.strength() != null && Double.isFinite(noise.strength()) && noise.strength() >= 0
				&& noise.strength() <= 1, "noise strength must be between 0 and 1.");
		validateSeed(noise.seed());
		NoiseModel.Builder builder = NoiseModel.builder();
		int first = noise.target() == null ? 0 : validateTarget(noise.target(), qubits);
		int end = noise.target() == null ? qubits : first + 1;
		for (int target = first; target < end; target++) {
			ErrorChannel channel = switch (noise.channel()) {
				case "phase-flip" -> ErrorChannel.phaseFlip(noise.strength(), target);
				case "depolarizing" -> ErrorChannel.depolarizing(noise.strength(), target);
				case "amplitude-damping" -> ErrorChannel.amplitudeDamping(noise.strength(), target);
				case "phase-damping" -> ErrorChannel.phaseDamping(noise.strength(), target);
				default -> throw ApiException.invalid("Unsupported noise channel.");
			};
			builder.afterAllGates(channel);
		}
		return builder.build();
	}

	private static StateVector validatedState(int qubits, List<Complex> amplitudes) {
		require(amplitudes != null && amplitudes.size() == (1 << qubits),
				"State must contain exactly 2^qubits complex amplitudes.");
		double[] data = new double[amplitudes.size() * 2];
		double norm = 0;
		for (int i = 0; i < amplitudes.size(); i++) {
			Complex amplitude = amplitudes.get(i);
			require(amplitude != null && Double.isFinite(amplitude.re()) && Double.isFinite(amplitude.im()),
					"Amplitudes must be finite complex values.");
			data[i * 2] = amplitude.re();
			data[i * 2 + 1] = amplitude.im();
			norm += amplitude.re() * amplitude.re() + amplitude.im() * amplitude.im();
		}
		require(Double.isFinite(norm) && Math.abs(norm - 1) <= 1e-9,
				"State amplitudes must be normalized (sum of squared magnitudes = 1).");
		return StateVector.fromArray(qubits, data);
	}

	private static List<Complex> amplitudes(StateVector state) {
		List<Complex> amplitudes = new ArrayList<>(state.dimension());
		for (int i = 0; i < state.dimension(); i++)
			amplitudes.add(new Complex(state.real(i), state.imag(i)));
		return amplitudes;
	}

	private static double[] probabilities(StateVector state) {
		double[] probabilities = new double[state.dimension()];
		for (int i = 0; i < state.dimension(); i++)
			probabilities[i] = state.real(i) * state.real(i) + state.imag(i) * state.imag(i);
		return probabilities;
	}

	private static int validateQubits(Integer qubits) {
		require(qubits != null && qubits >= 1 && qubits <= MAX_QUBITS,
				"qubits must be between 1 and " + MAX_QUBITS + ".");
		return qubits;
	}
	private static int validateTarget(Integer target, int qubits) {
		require(target != null && target >= 0 && target < qubits, "target must be a valid qubit index.");
		return target;
	}
	private static void validateMeasured(int qubits, int[] targets) {
		require(targets.length >= 1 && targets.length <= qubits, "measuredQubits must be nonempty and unique.");
		int[] sorted = targets.clone();
		Arrays.sort(sorted);
		for (int i = 0; i < sorted.length; i++) {
			validateTarget(sorted[i], qubits);
			require(i == 0 || sorted[i] != sorted[i - 1], "measuredQubits must be unique.");
		}
	}
	private static void validateSeed(Long seed) {
		require(seed == null || (seed >= -MAX_SAFE_INTEGER && seed <= MAX_SAFE_INTEGER),
				"seed must be a JavaScript-safe integer.");
	}
	private static void require(boolean condition, String message) {
		if (!condition)
			throw ApiException.invalid(message);
	}
	private <T> T bounded(Supplier<T> work) {
		if (!permits.tryAcquire())
			throw new ApiException(429, "BUSY", "The simulator is busy. Please retry shortly.");
		try {
			return work.get();
		} finally {
			permits.release();
		}
	}
}
