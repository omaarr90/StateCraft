package com.omaarr90.statecraft.core.noise;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.List;
import java.util.Objects;

/**
 * Amplitude damping error channel modeling energy relaxation.
 * <p>
 * Models the |1⟩ → |0⟩ decay process characteristic of T₁ relaxation. The
 * damping parameter γ represents the probability of energy loss.
 * <p>
 * Kraus operators:
 * <ul>
 * <li>K₀ = [[1, 0], [0, √(1-γ)]]</li>
 * <li>K₁ = [[0, √γ], [0, 0]]</li>
 * </ul>
 */
final class AmplitudeDampingChannel implements ErrorChannel {

	private final double gamma;
	private final int[] qubits;
	private final KrausDecomposition decomposition;

	AmplitudeDampingChannel(double gamma, int... qubits) {
		if (gamma < 0.0 || gamma > 1.0) {
			throw new IllegalArgumentException("gamma must be in [0,1], got " + gamma);
		}
		if (qubits.length != 1) {
			throw new IllegalArgumentException("amplitude damping channel currently only supports single qubits");
		}
		this.gamma = gamma;
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
		ComplexNumber sqrtGamma = new ComplexNumber(Math.sqrt(gamma), 0.0);
		ComplexNumber sqrtOneMinusGamma = new ComplexNumber(Math.sqrt(1.0 - gamma), 0.0);

		// K₀ = [[1, 0], [0, √(1-γ)]]
		// This operator preserves |0⟩ and partially preserves |1⟩
		KrausOperator k0 = ErrorChannel.singleQubitOperator(1.0 - gamma, // Probability that no decay occurs
				ComplexNumber.one(), ComplexNumber.zero(), ComplexNumber.zero(), sqrtOneMinusGamma);

		// K₁ = [[0, √γ], [0, 0]]
		// This operator causes |1⟩ → |0⟩ transition
		KrausOperator k1 = ErrorChannel.singleQubitOperator(gamma, // Probability of decay
				ComplexNumber.zero(), sqrtGamma, ComplexNumber.zero(), ComplexNumber.zero());

		return new KrausDecomposition(List.of(k0, k1), 1);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof AmplitudeDampingChannel other))
			return false;
		return Double.compare(gamma, other.gamma) == 0 && java.util.Arrays.equals(qubits, other.qubits);
	}

	@Override
	public int hashCode() {
		return Objects.hash(gamma, java.util.Arrays.hashCode(qubits));
	}

	@Override
	public String toString() {
		return "AmplitudeDampingChannel[γ=" + gamma + ", qubits=" + java.util.Arrays.toString(qubits) + "]";
	}
}
