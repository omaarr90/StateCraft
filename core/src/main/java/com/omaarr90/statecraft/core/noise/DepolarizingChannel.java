package com.omaarr90.statecraft.core.noise;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.List;
import java.util.Objects;

/**
 * Depolarizing error channel.
 * <p>
 * With probability p, applies a uniformly random Pauli error (X, Y, or Z). With
 * probability (1-p), leaves the qubit unchanged.
 * <p>
 * Kraus operators:
 * <ul>
 * <li>K₀ = √(1-p) · I</li>
 * <li>K₁ = √(p/3) · X</li>
 * <li>K₂ = √(p/3) · Y</li>
 * <li>K₃ = √(p/3) · Z</li>
 * </ul>
 */
final class DepolarizingChannel implements ErrorChannel {

	private final double probability;
	private final int[] qubits;
	private final KrausDecomposition decomposition;

	DepolarizingChannel(double probability, int... qubits) {
		if (probability < 0.0 || probability > 1.0) {
			throw new IllegalArgumentException("probability must be in [0,1], got " + probability);
		}
		if (qubits.length != 1) {
			throw new IllegalArgumentException("depolarizing channel currently only supports single qubits");
		}
		this.probability = probability;
		this.qubits = qubits.clone();
		this.decomposition = buildKrausDecomposition();
	}

	@Override
	public KrausDecomposition krausDecomposition() {
		return decomposition;
	}

	@Override
	public int[] affectedQubits() {
		return qubits.clone();
	}

	private KrausDecomposition buildKrausDecomposition() {
		double pNone = 1.0 - probability;
		double pEach = probability / 3.0;

		ComplexNumber sqrtPNone = new ComplexNumber(Math.sqrt(pNone), 0.0);
		ComplexNumber sqrtPEach = new ComplexNumber(Math.sqrt(pEach), 0.0);

		// K₀ = √(1-p) · I
		KrausOperator k0 = ErrorChannel.singleQubitOperator(sqrtPNone, ComplexNumber.zero(), ComplexNumber.zero(),
				sqrtPNone);

		// K₁ = √(p/3) · X
		KrausOperator k1 = ErrorChannel.singleQubitOperator(ComplexNumber.zero(), sqrtPEach, sqrtPEach,
				ComplexNumber.zero());

		// K₂ = √(p/3) · Y
		ComplexNumber sqrtPEachI = new ComplexNumber(0.0, Math.sqrt(pEach));
		ComplexNumber sqrtPEachNegI = new ComplexNumber(0.0, -Math.sqrt(pEach));
		KrausOperator k2 = ErrorChannel.singleQubitOperator(ComplexNumber.zero(), sqrtPEachNegI, sqrtPEachI,
				ComplexNumber.zero());

		// K₃ = √(p/3) · Z
		ComplexNumber sqrtPEachNeg = new ComplexNumber(-Math.sqrt(pEach), 0.0);
		KrausOperator k3 = ErrorChannel.singleQubitOperator(sqrtPEach, ComplexNumber.zero(), ComplexNumber.zero(),
				sqrtPEachNeg);

		return new KrausDecomposition(List.of(k0, k1, k2, k3), 1);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof DepolarizingChannel other))
			return false;
		return Double.compare(probability, other.probability) == 0 && java.util.Arrays.equals(qubits, other.qubits);
	}

	@Override
	public int hashCode() {
		return Objects.hash(probability, java.util.Arrays.hashCode(qubits));
	}

	@Override
	public String toString() {
		return "DepolarizingChannel[p=" + probability + ", qubits=" + java.util.Arrays.toString(qubits) + "]";
	}
}
