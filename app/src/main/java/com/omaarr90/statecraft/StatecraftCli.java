package com.omaarr90.statecraft;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.MeasurementResult;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.core.parse.CircuitFormat;
import com.omaarr90.statecraft.core.parse.CircuitParseException;
import com.omaarr90.statecraft.core.parse.CircuitParsers;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "statecraft",
        description = "Run Statecraft commands.",
        mixinStandardHelpOptions = true,
        version = StatecraftCli.VERSION,
        subcommands = { StatecraftCli.Engines.class, StatecraftCli.Demo.class, StatecraftCli.Run.class, StatecraftCli.Suite.class })
public final class StatecraftCli implements Callable<Integer> {
    static final String VERSION = "Statecraft CLI 0.1";

    @Spec
    private CommandSpec spec;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new StatecraftCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return CommandLine.ExitCode.OK;
    }

    @Command(name = "engines", description = "List available simulator engines.")
    static final class Engines implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            var loader = ServiceLoader.load(SimulatorEngine.class);
            var engines = new ArrayList<String>();
            for (var engine : loader) {
                engines.add(engine.id());
            }
            engines.sort(Comparator.naturalOrder());

            var out = spec.commandLine().getOut();
            out.println(VERSION + " - " + engines.size() + " engines discovered:");
            if (engines.isEmpty()) {
                out.println("  (none yet - add an engine in Phase 3)");
            } else {
                for (var id : engines) {
                    out.println("  - " + id);
                }
            }
            out.flush();
            return CommandLine.ExitCode.OK;
        }
    }

    @Command(name = "demo", description = "Run a sample circuit demonstrating the CNOT gate.")
    static final class Demo implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Option(names = "--shots", description = "Number of measurement shots to sample", defaultValue = "0")
        private int shots;

        @Option(names = "--seed", description = "Seed for measurement sampling (requires --shots)")
        private Long seed;

        @Option(names = "--samples", description = "Return raw samples instead of a histogram when measuring")
        private boolean samples;

        @Override
        public Integer call() {
            validateOptions();

            QuantumCircuit circuit = new QuantumCircuit(2)
                    .append(new Hadamard(), 0)
                    .append(CnotGate.of(), 0, 1);
            SimulationResult result = simulate(circuit);
            var out = spec.commandLine().getOut();
            result.finalState().ifPresentOrElse(
                    state -> {
                        out.println("Bell-state demo (qubit order q1 q0):");
                        printState(out, state, circuit.qubitCount());
                    },
                    () -> out.println("Final amplitudes omitted (shots-only request)."));
            result.measurement().ifPresent(measurement -> printMeasurement(out, measurement));
            out.flush();
            return CommandLine.ExitCode.OK;
        }

        private void validateOptions() {
            CommandLine commandLine = spec.commandLine();
            if (shots < 0) {
                throw new CommandLine.ParameterException(commandLine,
                        "--shots must be non-negative");
            }
            if (samples && shots == 0) {
                throw new CommandLine.ParameterException(commandLine,
                        "--samples requires --shots to be provided");
            }
            if (seed != null && shots == 0) {
                throw new CommandLine.ParameterException(commandLine,
                        "--seed requires --shots to be provided");
            }
        }

        private SimulationResult simulate(QuantumCircuit circuit) {
            SimulatorEngine engine = loadStatevectorEngine();
            SimulationRequest request = SimulationRequest.zeroState(circuit);
            if (shots > 0) {
                MeasurementInstruction instruction = samples
                        ? MeasurementInstruction.samplesAll(shots)
                        : MeasurementInstruction.countsAll(shots);
                if (seed != null) {
                    instruction = instruction.withSeed(seed);
                }
                request = request.withMeasurement(instruction);
            }
            return engine.simulate(request);
        }
    }

    @Command(name = "run", description = "Run a circuit from an input file.")
    static final class Run implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Option(names = "--input", required = true, description = "Path to the circuit file")
        private Path input;

        @Option(names = "--format", description = "Circuit format: {qasm|json|auto}", defaultValue = "auto")
        private String format;

        @Option(names = "--engine", description = "Simulator engine id", defaultValue = StatevectorEngineIdHolder.ID)
        private String engineId;

        @Option(names = "--shots", description = "Number of measurement shots to sample", defaultValue = "0")
        private int shots;

        @Option(names = "--seed", description = "Seed for measurement sampling (requires --shots)")
        private Long seed;

        @Option(names = "--samples", description = "Return raw samples instead of a histogram when measuring")
        private boolean samples;

        @Override
        public Integer call() {
            validateOptions();
            CircuitFormat selectedFormat = parseFormat();
            QuantumCircuit circuit = parseCircuit(selectedFormat);
            SimulatorEngine engine = loadEngine(engineId);
            SimulationResult result = engine.simulate(buildRequest(circuit));

            PrintWriter out = spec.commandLine().getOut();
            out.println("Running circuit from " + input.toAbsolutePath());
            result.finalState().ifPresentOrElse(
                    state -> {
                        out.println("Final state amplitudes (non-zero shown):");
                        printState(out, state, circuit.qubitCount());
                    },
                    () -> out.println("Final amplitudes omitted (shots-only request)."));
            result.measurement().ifPresent(measurement -> printMeasurement(out, measurement));
            out.flush();
            return CommandLine.ExitCode.OK;
        }

        private void validateOptions() {
            CommandLine commandLine = spec.commandLine();
            if (shots < 0) {
                throw new CommandLine.ParameterException(commandLine,
                        "--shots must be non-negative");
            }
            if (samples && shots == 0) {
                throw new CommandLine.ParameterException(commandLine,
                        "--samples requires --shots to be provided");
            }
            if (seed != null && shots == 0) {
                throw new CommandLine.ParameterException(commandLine,
                        "--seed requires --shots to be provided");
            }
        }

        private CircuitFormat parseFormat() {
            try {
                return CircuitFormat.fromOption(format);
            } catch (IllegalArgumentException e) {
                throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage(), e);
            }
        }

        private QuantumCircuit parseCircuit(CircuitFormat selectedFormat) {
            try {
                return CircuitParsers.parse(input, selectedFormat);
            } catch (CircuitParseException e) {
                throw new CommandLine.ParameterException(spec.commandLine(), formatParseError(e), e);
            }
        }

        private SimulationRequest buildRequest(QuantumCircuit circuit) {
            SimulationRequest request = SimulationRequest.zeroState(circuit);
            if (shots > 0) {
                MeasurementInstruction instruction = samples
                        ? MeasurementInstruction.samplesAll(shots)
                        : MeasurementInstruction.countsAll(shots);
                if (seed != null) {
                    instruction = instruction.withSeed(seed);
                }
                request = request.withMeasurement(instruction);
            }
            return request;
        }

        private SimulatorEngine loadEngine(String id) {
            var loader = ServiceLoader.load(SimulatorEngine.class);
            var engines = new ArrayList<SimulatorEngine>();
            for (var engine : loader) {
                engines.add(engine);
                if (engine.id().equals(id)) {
                    return engine;
                }
            }
            engines.sort(Comparator.comparing(SimulatorEngine::id));
            List<String> ids = engines.stream().map(SimulatorEngine::id).toList();
            String available = ids.isEmpty() ? "(none)" : String.join(", ", ids);
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Unknown engine '" + id + "'. Available: " + available);
        }

        private static String formatParseError(CircuitParseException e) {
            String message = e.getMessage();
            if (e.line().isPresent()) {
                message += " (line " + e.line().getAsInt();
                if (e.column().isPresent()) {
                    message += ", column " + e.column().getAsInt();
                }
                message += ")";
            }
            return message;
        }
    }

    @Command(name = "suite", description = "Execute a hard-coded suite of sample quantum algorithms.")
    static final class Suite implements Callable<Integer> {

        private static final int DEFAULT_SHOTS = 4_096;

        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            SimulatorEngine engine = loadStatevectorEngine();
            List<AlgorithmSpec> algorithms = List.of(
                    bellPair(),
                    ghzTriplet(),
                    qftOnBasisState());

            out.println(VERSION + " - executing " + algorithms.size()
                    + " algorithms using engine '" + engine.id() + "'");
            for (AlgorithmSpec algorithm : algorithms) {
                runAlgorithm(out, engine, algorithm);
            }
            out.flush();
            return CommandLine.ExitCode.OK;
        }

        private void runAlgorithm(PrintWriter out, SimulatorEngine engine, AlgorithmSpec algorithm) {
            out.println();
            out.println("=== " + algorithm.name() + " ===");
            out.println("Description : " + algorithm.description());
            out.println("Qubits      : " + algorithm.circuit().qubitCount());
            out.println("Depth       : " + algorithm.circuit().operations().size());
            out.println("Operations  :");
            printOperations(out, algorithm.circuit());

            SimulationResult result = engine.simulate(algorithm.request());
            result.finalState().ifPresent(state -> {
                out.println("Final state amplitudes (non-zero shown):");
                printState(out, state, algorithm.circuit().qubitCount());
            });
            result.measurement().ifPresent(measurement -> printMeasurement(out, measurement));
        }

        private static void printOperations(PrintWriter out, QuantumCircuit circuit) {
            List<QuantumCircuit.Operation> operations = circuit.operations();
            if (operations.isEmpty()) {
                out.println("  (no operations)");
                return;
            }
            for (int index = 0; index < operations.size(); index++) {
                out.println("  " + (index + 1) + ". " + describeOperation(operations.get(index)));
            }
        }

        private static AlgorithmSpec bellPair() {
            QuantumCircuit circuit = new QuantumCircuit(2)
                    .append(new Hadamard(), 0)
                    .append(CnotGate.of(), 0, 1);
            MeasurementInstruction measurement = MeasurementInstruction
                    .countsAll(DEFAULT_SHOTS)
                    .withSeed(0xBEEFL);
            SimulationRequest request = SimulationRequest.zeroState(circuit)
                    .withMeasurement(measurement);
            return new AlgorithmSpec(
                    "Bell Pair",
                    "Generates a maximally entangled two-qubit Bell state.",
                    circuit,
                    request);
        }

        private static AlgorithmSpec ghzTriplet() {
            QuantumCircuit circuit = new QuantumCircuit(3)
                    .append(new Hadamard(), 0)
                    .append(CnotGate.of(), 0, 1)
                    .append(CnotGate.of(), 0, 2);
            MeasurementInstruction measurement = MeasurementInstruction
                    .countsAll(DEFAULT_SHOTS)
                    .withSeed(0xFACE5EEDL);
            SimulationRequest request = SimulationRequest.zeroState(circuit)
                    .withMeasurement(measurement);
            return new AlgorithmSpec(
                    "GHZ State",
                    "Prepares the three-qubit Greenberger–Horne–Zeilinger entangled state.",
                    circuit,
                    request);
        }

        private static AlgorithmSpec qftOnBasisState() {
            int qubits = 3;
            QuantumCircuit circuit = buildQftCircuit(qubits);
            StateVector initial = basisState(qubits, 0b101);
            MeasurementInstruction measurement = MeasurementInstruction
                    .countsAll(DEFAULT_SHOTS)
                    .withSeed(0xF0F0F0F0L);
            SimulationRequest request = SimulationRequest.withInitialState(circuit, initial)
                    .withMeasurement(measurement);
            return new AlgorithmSpec(
                    "Quantum Fourier Transform (3-qubit)",
                    "Applies the QFT to the computational basis state |101⟩, exposing the frequency-domain amplitudes.",
                    circuit,
                    request);
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

        private record AlgorithmSpec(
                String name,
                String description,
                QuantumCircuit circuit,
                SimulationRequest request) {
        }
    }

    private static SimulatorEngine loadStatevectorEngine() {
        return ServiceLoader.load(SimulatorEngine.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(engine -> StatevectorEngineIdHolder.ID.equals(engine.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No simulator engine with id '" + StatevectorEngineIdHolder.ID + "' found"));
    }

    private static void printState(PrintWriter out, StateVector state, int qubitCount) {
        int dimension = state.dimension();
        boolean printed = false;
        for (int index = 0; index < dimension; index++) {
            double real = state.real(index);
            double imag = state.imag(index);
            if (isZero(real, imag)) {
                continue;
            }
            String bits = toBitString(index, qubitCount);
            out.println("  |" + bits + "> : " + formatAmplitude(real, imag));
            printed = true;
        }
        if (!printed) {
            out.println("  (all amplitudes are numerically ~0)");
        }
    }

    private static void printMeasurement(PrintWriter out, MeasurementResult measurement) {
        String qubitLabel = formatMeasuredQubits(measurement.measuredQubits());
        if (measurement instanceof MeasurementResult.Histogram histogram) {
            out.println("Shot histogram (qubits " + qubitLabel + "):");
            histogram.counts().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        String bits = toMeasuredBitString(entry.getKey(), histogram.measuredQubits());
                        out.println("  " + bits + " : " + entry.getValue());
                    });
        } else if (measurement instanceof MeasurementResult.Samples samplesResult) {
            out.println("Shot samples (qubits " + qubitLabel + "):");
            int limit = Math.min(samplesResult.shots(), 32);
            for (int index = 0; index < limit; index++) {
                String bits = toMeasuredBitString(samplesResult.outcomes().get(index), samplesResult.measuredQubits());
                out.println("  " + bits);
            }
            int remaining = samplesResult.shots() - limit;
            if (remaining > 0) {
                out.println("  ... (" + remaining + " more)");
            }
        }
    }

    private static String toBitString(int index, int qubitCount) {
        StringBuilder sb = new StringBuilder(qubitCount);
        for (int qubit = qubitCount - 1; qubit >= 0; qubit--) {
            sb.append((index >> qubit) & 1);
        }
        return sb.toString();
    }

    private static String formatAmplitude(double real, double imag) {
        final double eps = 1e-9;
        final double invSqrt2 = 1.0 / Math.sqrt(2.0);
        boolean realZero = Math.abs(real) < eps;
        boolean imagZero = Math.abs(imag) < eps;

        if (realZero && imagZero) {
            return "0";
        }

        if (imagZero) {
            if (Math.abs(real - invSqrt2) < eps) {
                return "1/sqrt(2)";
            }
            if (Math.abs(real + invSqrt2) < eps) {
                return "-1/sqrt(2)";
            }
            if (Math.abs(real - 1.0) < eps) {
                return "1";
            }
            if (Math.abs(real + 1.0) < eps) {
                return "-1";
            }
            return String.format(Locale.US, "%.6f", real);
        }

        if (realZero) {
            if (Math.abs(imag - invSqrt2) < eps) {
                return "i/sqrt(2)";
            }
            if (Math.abs(imag + invSqrt2) < eps) {
                return "-i/sqrt(2)";
            }
            if (Math.abs(imag - 1.0) < eps) {
                return "i";
            }
            if (Math.abs(imag + 1.0) < eps) {
                return "-i";
            }
            return String.format(Locale.US, "%.6fi", imag);
        }

        return String.format(Locale.US, "%.6f %s %.6fi",
                real, imag >= 0.0 ? "+" : "-", Math.abs(imag));
    }

    private static String toMeasuredBitString(int outcome, int[] measuredQubits) {
        StringBuilder sb = new StringBuilder(measuredQubits.length);
        for (int offset = measuredQubits.length - 1; offset >= 0; offset--) {
            sb.append((outcome >> offset) & 1);
        }
        return sb.toString();
    }

    private static String formatMeasuredQubits(int[] measuredQubits) {
        if (measuredQubits.length == 0) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        for (int index = measuredQubits.length - 1; index >= 0; index--) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append('q').append(measuredQubits[index]);
        }
        return sb.toString();
    }

    private static boolean isZero(double real, double imag) {
        final double eps = 1e-9;
        return Math.abs(real) < eps && Math.abs(imag) < eps;
    }

    private static String describeOperation(QuantumCircuit.Operation operation) {
        if (operation instanceof QuantumCircuit.Operation.SingleGateOperation single) {
            return single.gate().getClass().getSimpleName() + "(q" + single.qubit() + ")";
        }
        if (operation instanceof QuantumCircuit.Operation.CnotOperation cnot) {
            return "CNOT(control=q" + cnot.controlQubit() + ", target=q" + cnot.targetQubit() + ")";
        }
        if (operation instanceof QuantumCircuit.Operation.TwoQubitGateOperation twoQubit) {
            return "Two-qubit unitary(q" + twoQubit.firstQubit() + ", q" + twoQubit.secondQubit() + ")";
        }
        if (operation instanceof QuantumCircuit.Operation.TwoQubitDiagonalOperation diagonal) {
            return "Diagonal two-qubit(q" + diagonal.firstQubit() + ", q" + diagonal.secondQubit() + ")";
        }
        if (operation instanceof QuantumCircuit.Operation.SwapOperation swap) {
            return "SWAP(q" + swap.firstQubit() + ", q" + swap.secondQubit() + ")";
        }
        if (operation instanceof QuantumCircuit.Operation.MultiControlOperation multi) {
            return multi.gate().getClass().getSimpleName()
                    + "(controls=" + formatQubitList(multi.controlQubits())
                    + ", target=q" + multi.targetQubit() + ")";
        }
        if (operation instanceof QuantumCircuit.Operation.MeasureOperation measure) {
            return "MEASURE " + formatQubitList(measure.qubits());
        }
        return operation.getClass().getSimpleName();
    }

    private static String formatQubitList(int[] qubits) {
        if (qubits.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int index = 0; index < qubits.length; index++) {
            if (index > 0) {
                sb.append(", ");
            }
            sb.append("q").append(qubits[index]);
        }
        sb.append(']');
        return sb.toString();
    }

    private static final class StatevectorEngineIdHolder {
        private static final String ID = "statevector";

        private StatevectorEngineIdHolder() {
        }
    }
}
