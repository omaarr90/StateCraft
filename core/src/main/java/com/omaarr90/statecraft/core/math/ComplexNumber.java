package com.omaarr90.statecraft.core.math;

/**
 * Immutable complex number with tiny helper methods.
 */
public record ComplexNumber(double real, double imag) {

	private static final ComplexNumber ZERO = new ComplexNumber(0.0, 0.0);
	private static final ComplexNumber ONE = new ComplexNumber(1.0, 0.0);

	public static ComplexNumber zero() {
		return ZERO;
	}

	public static ComplexNumber one() {
		return ONE;
	}

	public ComplexNumber plus(ComplexNumber other) {
		return new ComplexNumber(real + other.real, imag + other.imag);
	}

	public ComplexNumber minus(ComplexNumber other) {
		return new ComplexNumber(real - other.real, imag - other.imag);
	}

	public ComplexNumber negate() {
		return new ComplexNumber(-real, -imag);
	}

	public ComplexNumber scale(double factor) {
		return new ComplexNumber(real * factor, imag * factor);
	}

	public ComplexNumber times(ComplexNumber other) {
		double rr = real * other.real - imag * other.imag;
		double ri = real * other.imag + imag * other.real;
		return new ComplexNumber(rr, ri);
	}

	public ComplexNumber conjugate() {
		return new ComplexNumber(real, -imag);
	}

	public double magnitudeSquared() {
		return real * real + imag * imag;
	}
}
