package com.omaarr90.statecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class StatecraftCliTest {

    @Test
    void demoPrintsHistogramWhenShotsRequested() {
        StringWriter out = new StringWriter();
        CommandLine cli = new CommandLine(new StatecraftCli());
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(new StringWriter(), true));

        int exitCode = cli.execute("demo", "--shots", "64", "--seed", "17");
        assertEquals(CommandLine.ExitCode.OK, exitCode);

        String output = out.toString();
        assertTrue(output.contains("Bell-state demo"));
        assertTrue(output.contains("Shot histogram"));
        assertTrue(output.contains("  00 :") || output.contains("  11 :"));
    }

    @Test
    void demoPrintsSamplesWhenRequested() {
        StringWriter out = new StringWriter();
        CommandLine cli = new CommandLine(new StatecraftCli());
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(new StringWriter(), true));

        int exitCode = cli.execute("demo", "--shots", "8", "--samples", "--seed", "5");
        assertEquals(CommandLine.ExitCode.OK, exitCode);

        String output = out.toString();
        assertTrue(output.contains("Shot samples"));
        assertTrue(output.contains("  0") || output.contains("  1") || output.contains("  00"));
    }

    @Test
    void samplesWithoutShotsFailsValidation() {
        StringWriter err = new StringWriter();
        CommandLine cli = new CommandLine(new StatecraftCli());
        cli.setOut(new PrintWriter(new StringWriter(), true));
        cli.setErr(new PrintWriter(err, true));

        int exitCode = cli.execute("demo", "--samples");
        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        assertTrue(err.toString().contains("--samples requires --shots"));
    }

    @Test
    void omitFinalStateRequiresShots() {
        StringWriter err = new StringWriter();
        CommandLine cli = new CommandLine(new StatecraftCli());
        cli.setOut(new PrintWriter(new StringWriter(), true));
        cli.setErr(new PrintWriter(err, true));

        int exitCode = cli.execute("demo", "--omit-final-state");
        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        assertTrue(err.toString().contains("--omit-final-state requires --shots"));
    }

    @Test
    void demoOmitsAmplitudesWhenRequested() {
        StringWriter out = new StringWriter();
        CommandLine cli = new CommandLine(new StatecraftCli());
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(new StringWriter(), true));

        int exitCode = cli.execute("demo", "--shots", "16", "--omit-final-state", "--seed", "9");
        assertEquals(CommandLine.ExitCode.OK, exitCode);

        String output = out.toString();
        assertTrue(output.contains("Final amplitudes omitted"));
        assertTrue(output.contains("Shot histogram"));
    }

    @Test
    void runPrintsLargeBitstringHistogram() throws IOException {
        Path input = Files.createTempFile("statecraft-large", ".json");
        Files.writeString(input, """
                {
                  "qubits": 40,
                  "operations": []
                }
                """);

        StringWriter out = new StringWriter();
        CommandLine cli = new CommandLine(new StatecraftCli());
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(new StringWriter(), true));

        int exitCode = cli.execute(
                "run",
                "--input",
                input.toString(),
                "--format",
                "json",
                "--engine",
                "stabilizer",
                "--shots",
                "2",
                "--omit-final-state");
        assertEquals(CommandLine.ExitCode.OK, exitCode);

        String output = out.toString();
        assertTrue(output.contains("Final amplitudes omitted"));
        assertTrue(output.contains("0000000000000000000000000000000000000000 : 2"));
    }

    @Test
    void runNoiseFlagsAffectFinalStateDeterministically() throws IOException {
        Path input = Files.createTempFile("statecraft-noisy-run", ".json");
        Files.writeString(input, """
                {
                  "qubits": 1,
                  "operations": [
                    { "gate": "x", "target": 0 }
                  ]
                }
                """);

        StringWriter baselineOut = new StringWriter();
        CommandLine baselineCli = new CommandLine(new StatecraftCli());
        baselineCli.setOut(new PrintWriter(baselineOut, true));
        baselineCli.setErr(new PrintWriter(new StringWriter(), true));

        int baselineExit = baselineCli.execute(
                "run",
                "--input",
                input.toString(),
                "--format",
                "json");
        assertEquals(CommandLine.ExitCode.OK, baselineExit);
        assertTrue(baselineOut.toString().contains("|1> : 1"));

        StringWriter noisyOut = new StringWriter();
        CommandLine noisyCli = new CommandLine(new StatecraftCli());
        noisyCli.setOut(new PrintWriter(noisyOut, true));
        noisyCli.setErr(new PrintWriter(new StringWriter(), true));

        int noisyExit = noisyCli.execute(
                "run",
                "--input",
                input.toString(),
                "--format",
                "json",
                "--noise-amplitude-damping",
                "1.0",
                "--noise-seed",
                "7");
        assertEquals(CommandLine.ExitCode.OK, noisyExit);

        String output = noisyOut.toString();
        assertTrue(output.contains("|0> : 1"));
        assertFalse(output.contains("|1> : 1"));
    }

    @Test
    void runNoiseConfigAppliesGlobalNoise() throws IOException {
        Path input = Files.createTempFile("statecraft-noise-config-input", ".json");
        Files.writeString(input, """
                {
                  "qubits": 1,
                  "operations": [
                    { "gate": "x", "target": 0 }
                  ]
                }
                """);
        Path config = Files.createTempFile("statecraft-noise-config", ".json");
        Files.writeString(config, """
                {
                  "noiseSeed": 99,
                  "global": {
                    "amplitudeDamping": 1.0
                  }
                }
                """);

        StringWriter out = new StringWriter();
        CommandLine cli = new CommandLine(new StatecraftCli());
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(new StringWriter(), true));

        int exitCode = cli.execute(
                "run",
                "--input",
                input.toString(),
                "--format",
                "json",
                "--noise-config",
                config.toString());
        assertEquals(CommandLine.ExitCode.OK, exitCode);

        assertTrue(out.toString().contains("|0> : 1"));
    }

    @Test
    void demoAcceptsNoiseFlags() {
        StringWriter out = new StringWriter();
        CommandLine cli = new CommandLine(new StatecraftCli());
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(new StringWriter(), true));

        int exitCode = cli.execute("demo", "--noise-phase-flip", "1.0", "--noise-seed", "3");
        assertEquals(CommandLine.ExitCode.OK, exitCode);

        String output = out.toString();
        assertTrue(output.contains("Bell-state demo"));
        assertTrue(output.contains("-1/sqrt(2)"));
    }

    @Test
    void suiteAcceptsNoiseFlags() {
        StringWriter out = new StringWriter();
        CommandLine cli = new CommandLine(new StatecraftCli());
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(new StringWriter(), true));

        int exitCode = cli.execute("suite", "--noise-phase-flip", "0.0", "--noise-seed", "42");
        assertEquals(CommandLine.ExitCode.OK, exitCode);

        String output = out.toString();
        assertTrue(output.contains("executing 3 algorithms"));
        assertTrue(output.contains("=== Bell Pair ==="));
    }
}
