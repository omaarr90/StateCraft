package com.omaarr90.statecraft;

import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

@Command(
        name = "statecraft",
        description = "Run Statecraft commands.",
        mixinStandardHelpOptions = true,
        version = StatecraftCli.VERSION,
        subcommands = { StatecraftCli.Engines.class, StatecraftCli.Demo.class }
)
public final class StatecraftCli implements Callable<Integer> {
    static final String VERSION = "Statecraft CLI 0.1";

    @Spec
    private CommandSpec spec;

    void main(String[] args) {
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
            out.println(VERSION + " – engines discovered:");
            if (engines.isEmpty()) {
                out.println("  (none yet — add an engine in Phase 3)");
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

        @Override
        public Integer call() {
            QuantumCircuit circuit = new QuantumCircuit(2)
                    .append(new Hadamard(), 0)
                    .append(CnotGate.of(), 0, 1);
            ComplexNumber[] result = circuit.apply();
            var out = spec.commandLine().getOut();
            out.println("Bell-state demo (qubit order q1 q0):");
            for (int index = 0; index < result.length; index++) {
                ComplexNumber amp = result[index];
                if (isZero(amp)) {
                    continue;
                }
                String bits = toBitString(index, circuit.qubitCount());
                out.println("  |" + bits + "> : " + formatAmplitude(amp));
            }
            out.flush();
            return CommandLine.ExitCode.OK;
        }

        private static String toBitString(int index, int qubitCount) {
            StringBuilder sb = new StringBuilder(qubitCount);
            for (int qubit = qubitCount - 1; qubit >= 0; qubit--) {
                sb.append(((index >> qubit) & 1));
            }
            return sb.toString();
        }

        private static String formatAmplitude(ComplexNumber amplitude) {
            final double eps = 1e-9;
            final double invSqrt2 = 1.0 / Math.sqrt(2.0);
            double real = amplitude.real();
            double imag = amplitude.imag();
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

        private static boolean isZero(ComplexNumber amplitude) {
            final double eps = 1e-9;
            return Math.abs(amplitude.real()) < eps && Math.abs(amplitude.imag()) < eps;
        }
    }
}
