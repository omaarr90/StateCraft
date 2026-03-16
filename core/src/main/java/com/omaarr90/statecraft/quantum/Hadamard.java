package com.omaarr90.statecraft.quantum;

import com.omaarr90.statecraft.core.math.ComplexNumber;

public record Hadamard() implements SingleQubitGate {

	private static final double SCALE = 1.0 / Math.sqrt(2.0);
	private static final ComplexNumber M00 = new ComplexNumber(SCALE, 0.0);
	private static final ComplexNumber M01 = new ComplexNumber(SCALE, 0.0);
	private static final ComplexNumber M10 = new ComplexNumber(SCALE, 0.0);
	private static final ComplexNumber M11 = new ComplexNumber(-SCALE, 0.0);

	@Override
	public String name() {
		return "H";
	}

	@Override
	public ComplexNumber element(int row, int col) {
		return switch (row) {
			case 0 -> switch (col) {
				case 0 -> M00;
				case 1 -> M01;
				default -> throw new IllegalArgumentException("col must be 0 or 1: " + col);
			};
			case 1 -> switch (col) {
				case 0 -> M10;
				case 1 -> M11;
				default -> throw new IllegalArgumentException("col must be 0 or 1: " + col);
			};
			default -> throw new IllegalArgumentException("row must be 0 or 1: " + row);
		};
	}
}
