package com.omaarr90.statecraft.core.noise;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.List;
import java.util.Objects;

/**
 * Phase damping (dephasing) channel modeling pure dephasing without energy
 * loss.
 * <p>
 * Models loss of quantum coherence that affects the off-diagonal elements of
 * the density matrix without changing the populations.
 * <p>
 * Kraus operators:
 * <ul>
 * <li>K₀ = [[1, 0], [0, √(1-λ)]]</li>
 * <li>K₁ = [[0, 0], [0, √λ]]</li>
 * </ul>
 *
 * @param lambda
 *            dephasing parameter in [0,1]
 */
final class PhaseDampingChannel implements ErrorChannel {

	private final double lambda;
	private final int[] qubits;
	private final KrausDecomposition decomposition;

	PhaseDampingChannel(double lambda, int... qubits) {
		if (lambda < 0.0 || lambda > 1.0) {
			throw new IllegalArgumentException("lambda must be in [0,1], got " + lambda);
		}
		if (qubits.length != 1) {
			throw new IllegalArgumentException("phase damping channel currently only supports single qubits");
		}
		this.lambda = lambda;
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
		ComplexNumber sqrtLambda = new ComplexNumber(Math.sqrt(lambda), 0.0);
		ComplexNumber sqrtOneMinusLambda = new ComplexNumber(Math.sqrt(1.0 - lambda), 0.0);

		// K₀ = [[1, 0], [0, √(1-λ)]]
		KrausOperator k0 = ErrorChannel.singleQubitOperator(ComplexNumber.one(), ComplexNumber.zero(),
				ComplexNumber.zero(), sqrtOneMinusLambda);

		// K₁ = [[0, 0], [0, √λ]]
		KrausOperator k1 = ErrorChannel.singleQubitOperator(ComplexNumber.zero(), ComplexNumber.zero(),
				ComplexNumber.zero(), sqrtLambda);

		return new KrausDecomposition(List.of(k0, k1), 1);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof PhaseDampingChannel other))
			return false;
		return Double.compare(lambda, other.lambda) == 0 && java.util.Arrays.equals(qubits, other.qubits);
	}

	@Override
	public int hashCode() {
		return Objects.hash(lambda, java.util.Arrays.hashCode(qubits));
	}

	@Override
	public String toString() {
		return "PhaseDampingChannel[λ=" + lambda + ", qubits=" + java.util.Arrays.toString(qubits) + "]";
	}
}
