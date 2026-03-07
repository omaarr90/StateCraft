package com.omaarr90.statecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
