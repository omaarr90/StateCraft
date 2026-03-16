package com.omaarr90.statecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class NoiseOptionsSupportTest {

	@Test
	void jsonConfigParsesAndBuildsNoiseModel() throws IOException {
		Path config = writeTemp("""
				{
				  "noiseSeed": 1234,
				  "global": {
				    "depolarizing": 0.01,
				    "phaseFlip": 0.02
				  }
				}
				""");

		NoiseOptionsSupport.ResolvedNoiseSpec spec = resolve(
				new NoiseOptionsSupport.NoiseOptionsInput(config, null, null, null, null, null, null, null, null));

		assertTrue(spec.hasNoiseChannels());
		assertTrue(spec.noiseSeed().isPresent());
		assertEquals(1234L, spec.noiseSeed().getAsLong());

		var operation = new QuantumCircuit.Operation.SingleGateOperation(new PauliX(), 0);
		assertEquals(4, spec.toNoiseModel(2).channelsAfter(operation).size());
	}

	@Test
	void invalidJsonSchemaFailsFast() throws IOException {
		Path config = writeTemp("""
				[1, 2, 3]
				""");

		CommandLine.ParameterException error = assertThrows(CommandLine.ParameterException.class, () -> resolve(
				new NoiseOptionsSupport.NoiseOptionsInput(config, null, null, null, null, null, null, null, null)));

		assertTrue(error.getMessage().contains("must be a JSON object"));
	}

	@Test
	void configAndFlagsAreMerged() throws IOException {
		Path config = writeTemp("""
				{
				  "global": {
				    "depolarizing": 0.01
				  }
				}
				""");

		NoiseOptionsSupport.ResolvedNoiseSpec spec = resolve(
				new NoiseOptionsSupport.NoiseOptionsInput(config, null, null, null, 0.25, null, null, null, null));

		var operation = new QuantumCircuit.Operation.SingleGateOperation(new PauliX(), 0);
		assertEquals(6, spec.toNoiseModel(3).channelsAfter(operation).size());
	}

	@Test
	void cliNoiseSeedOverridesConfigSeed() throws IOException {
		Path config = writeTemp("""
				{
				  "noiseSeed": 11,
				  "global": {
				    "phaseFlip": 0.2
				  }
				}
				""");

		NoiseOptionsSupport.ResolvedNoiseSpec spec = resolve(
				new NoiseOptionsSupport.NoiseOptionsInput(config, 22L, null, null, null, null, null, null, null));

		assertTrue(spec.noiseSeed().isPresent());
		assertEquals(22L, spec.noiseSeed().getAsLong());
	}

	@Test
	void bomPrefixedConfigParsesAndBuildsNoiseModel() throws IOException {
		Path config = writeTemp("\uFEFF" + """
				{
				  "noiseSeed": 1234,
				  "global": {
				    "amplitudeDamping": 1.0
				  }
				}
				""");

		NoiseOptionsSupport.ResolvedNoiseSpec spec = resolve(
				new NoiseOptionsSupport.NoiseOptionsInput(config, null, null, null, null, null, null, null, null));

		assertTrue(spec.hasNoiseChannels());
		assertTrue(spec.noiseSeed().isPresent());
		assertEquals(1234L, spec.noiseSeed().getAsLong());
	}

	@Test
	void configSeedOnlyIsRejected() throws IOException {
		Path config = writeTemp("""
				{
				  "noiseSeed": 1234
				}
				""");

		CommandLine.ParameterException error = assertThrows(CommandLine.ParameterException.class, () -> resolve(
				new NoiseOptionsSupport.NoiseOptionsInput(config, null, null, null, null, null, null, null, null)));

		assertTrue(error.getMessage().contains("noiseSeed"));
		assertTrue(error.getMessage().contains("requires at least one noise channel source"));
	}

	@Test
	void configSeedCanBeUsedWhenFlagsProvideChannels() throws IOException {
		Path config = writeTemp("""
				{
				  "noiseSeed": 77
				}
				""");

		NoiseOptionsSupport.ResolvedNoiseSpec spec = resolve(
				new NoiseOptionsSupport.NoiseOptionsInput(config, null, null, 0.25, null, null, null, null, null));

		assertTrue(spec.hasNoiseChannels());
		assertTrue(spec.noiseSeed().isPresent());
		assertEquals(77L, spec.noiseSeed().getAsLong());
	}

	@Test
	void partialThermalFlagsAreRejected() {
		CommandLine.ParameterException error = assertThrows(CommandLine.ParameterException.class, () -> resolve(
				new NoiseOptionsSupport.NoiseOptionsInput(null, null, null, null, null, null, 5e-5, null, null)));

		assertTrue(error.getMessage().contains("must be provided together"));
	}

	@Test
	void noiseSeedWithoutChannelsIsRejected() {
		CommandLine.ParameterException error = assertThrows(CommandLine.ParameterException.class, () -> resolve(
				new NoiseOptionsSupport.NoiseOptionsInput(null, 123L, null, null, null, null, null, null, null)));

		assertTrue(error.getMessage().contains("--noise-seed requires at least one noise channel source"));
	}

	private static NoiseOptionsSupport.ResolvedNoiseSpec resolve(NoiseOptionsSupport.NoiseOptionsInput input) {
		CommandLine commandLine = new CommandLine(new StatecraftCli());
		commandLine.setOut(new PrintWriter(new StringWriter(), true));
		commandLine.setErr(new PrintWriter(new StringWriter(), true));
		return NoiseOptionsSupport.resolve(input, commandLine);
	}

	private static Path writeTemp(String content) throws IOException {
		Path path = Files.createTempFile("statecraft-noise", ".json");
		Files.writeString(path, content);
		return path;
	}
}
