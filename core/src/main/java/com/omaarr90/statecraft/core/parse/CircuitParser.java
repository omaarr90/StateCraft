package com.omaarr90.statecraft.core.parse;

import com.omaarr90.statecraft.quantum.QuantumCircuit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public interface CircuitParser {

	QuantumCircuit parse(String source);

	default QuantumCircuit parse(Path path) {
		Objects.requireNonNull(path, "path");
		try {
			return parse(Files.readString(path, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new CircuitParseException("Failed to read circuit file: " + path, e);
		}
	}
}
