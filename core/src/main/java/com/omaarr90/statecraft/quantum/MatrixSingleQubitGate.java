package com.omaarr90.statecraft.quantum;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.Objects;

public record MatrixSingleQubitGate(String name, ComplexNumber m00, ComplexNumber m01, ComplexNumber m10,
		ComplexNumber m11) implements SingleQubitGate {

	public MatrixSingleQubitGate {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(m00, "m00");
		Objects.requireNonNull(m01, "m01");
		Objects.requireNonNull(m10, "m10");
		Objects.requireNonNull(m11, "m11");
	}

	@Override
	public ComplexNumber element(int row, int col) {
		return switch (row) {
			case 0 -> switch (col) {
				case 0 -> m00;
				case 1 -> m01;
				default -> throw invalidColumn(col);
			};
			case 1 -> switch (col) {
				case 0 -> m10;
				case 1 -> m11;
				default -> throw invalidColumn(col);
			};
			default -> throw invalidRow(row);
		};
	}

	private IllegalArgumentException invalidRow(int row) {
		return new IllegalArgumentException("row must be 0 or 1, got " + row);
	}

	private IllegalArgumentException invalidColumn(int col) {
		return new IllegalArgumentException("col must be 0 or 1, got " + col);
	}
}
