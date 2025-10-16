package com.omaarr90.statecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
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
}
