package com.omaarr90.statecraft.core.parse;

import com.omaarr90.statecraft.quantum.QuantumCircuit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class CircuitParsers {

	private static final CircuitParser JSON_PARSER = new JsonCircuitParser();
	private static final CircuitParser QASM_PARSER = new OpenQasmCircuitParser();

	private CircuitParsers() {
	}

	public static QuantumCircuit parse(Path path, CircuitFormat format) {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(format, "format");
		String source = readSource(path);
		CircuitParser parser = selectParser(format, path, source);
		return parser.parse(source);
	}

	public static CircuitParser selectParser(CircuitFormat format, Path path, String source) {
		Objects.requireNonNull(format, "format");
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(source, "source");
		CircuitFormat resolved = format == CircuitFormat.AUTO ? CircuitFormatDetector.detect(path, source) : format;
		return switch (resolved) {
			case JSON -> JSON_PARSER;
			case QASM -> QASM_PARSER;
			case AUTO -> throw new IllegalStateException("AUTO format must be resolved before parser selection");
		};
	}

	private static String readSource(Path path) {
		try {
			return normalizeSource(Files.readString(path, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new CircuitParseException("Failed to read circuit file: " + path, e);
		}
	}

	private static String normalizeSource(String source) {
		if (!source.isEmpty() && source.charAt(0) == '\uFEFF') {
			return source.substring(1);
		}
		return source;
	}
}
