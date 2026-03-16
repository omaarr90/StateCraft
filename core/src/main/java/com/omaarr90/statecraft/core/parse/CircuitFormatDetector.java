package com.omaarr90.statecraft.core.parse;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public final class CircuitFormatDetector {

	private CircuitFormatDetector() {
	}

	public static CircuitFormat detect(Path path, String source) {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(source, "source");
		CircuitFormat byExtension = detectByExtension(path);
		if (byExtension != null) {
			return byExtension;
		}
		CircuitFormat byContent = detectByContent(source);
		if (byContent != null) {
			return byContent;
		}
		throw new CircuitParseException("Unable to detect circuit format; specify --format qasm or --format json.");
	}

	static CircuitFormat detectByExtension(Path path) {
		String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
		if (fileName.endsWith(".qasm") || fileName.endsWith(".qasm3")) {
			return CircuitFormat.QASM;
		}
		if (fileName.endsWith(".json")) {
			return CircuitFormat.JSON;
		}
		return null;
	}

	static CircuitFormat detectByContent(String source) {
		String trimmed = stripLeadingTrivia(source);
		if (trimmed.startsWith("OPENQASM")) {
			return CircuitFormat.QASM;
		}
		return null;
	}

	private static String stripLeadingTrivia(String source) {
		int index = 0;
		int length = source.length();
		while (index < length) {
			char ch = source.charAt(index);
			if (ch == '\uFEFF' || Character.isWhitespace(ch)) {
				index++;
				continue;
			}
			if (ch == '/' && index + 1 < length) {
				char next = source.charAt(index + 1);
				if (next == '/') {
					index += 2;
					while (index < length && source.charAt(index) != '\n') {
						index++;
					}
					continue;
				}
				if (next == '*') {
					index += 2;
					while (index + 1 < length) {
						if (source.charAt(index) == '*' && source.charAt(index + 1) == '/') {
							index += 2;
							break;
						}
						index++;
					}
					continue;
				}
			}
			break;
		}
		return source.substring(index);
	}
}
