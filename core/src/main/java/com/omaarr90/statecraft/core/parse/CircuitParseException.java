package com.omaarr90.statecraft.core.parse;

import java.util.OptionalInt;

public final class CircuitParseException extends RuntimeException {

	private final OptionalInt line;
	private final OptionalInt column;

	public CircuitParseException(String message) {
		this(message, OptionalInt.empty(), OptionalInt.empty(), null);
	}

	public CircuitParseException(String message, Throwable cause) {
		this(message, OptionalInt.empty(), OptionalInt.empty(), cause);
	}

	public CircuitParseException(String message, int line, int column) {
		this(message, normalize(line), normalize(column), null);
	}

	public CircuitParseException(String message, int line, int column, Throwable cause) {
		this(message, normalize(line), normalize(column), cause);
	}

	private CircuitParseException(String message, OptionalInt line, OptionalInt column, Throwable cause) {
		super(message, cause);
		this.line = line;
		this.column = column;
	}

	public OptionalInt line() {
		return line;
	}

	public OptionalInt column() {
		return column;
	}

	private static OptionalInt normalize(int value) {
		return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
	}
}
