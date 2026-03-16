package com.omaarr90.statecraft.core.noise;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.List;
import java.util.Objects;

/**
 * Phase flip error channel.
 * <p>
 * With probability p, applies a Z gate (phase flip). With probability (1-p),
 * leaves the qubit unchanged.
 * <p>
 * This models random phase errors that preserve population but destroy
 * superpositions by flipping the relative phase.
 * <p>
 * Kraus operators:
 * <ul>
 * <li>K₀ = √(1-p) · I (probability: 1-p)</li>
 * <li>K₁ = √p · Z (probability: p)</li>
 * </ul>
 */
final class PhaseFlipChannel implements ErrorChannel {

	private final double probability;
	private final int[] qubits;
	private final KrausDecomposition decomposition;

	PhaseFlipChannel(double probability, int... qubits) {
		if (probability < 0.0 || probability > 1.0) {
			throw new IllegalArgumentException("probability must be in [0,1], got " + probability);
		}
		if (qubits.length != 1) {
			throw new IllegalArgumentException("phase flip channel currently only supports single qubits");
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
		ComplexNumber sqrtP = new ComplexNumber(Math.sqrt(probability), 0.0);
		ComplexNumber sqrtOneMinusP = new ComplexNumber(Math.sqrt(1.0 - probability), 0.0);

		// K₀ = √(1-p) · I
		KrausOperator k0 = ErrorChannel.singleQubitOperator(1.0 - probability, sqrtOneMinusP, ComplexNumber.zero(),
				ComplexNumber.zero(), sqrtOneMinusP);

		// K₁ = √p · Z = √p · [[1, 0], [0, -1]]
		ComplexNumber sqrtPNeg = new ComplexNumber(-Math.sqrt(probability), 0.0);
		KrausOperator k1 = ErrorChannel.singleQubitOperator(probability, sqrtP, ComplexNumber.zero(),
				ComplexNumber.zero(), sqrtPNeg);

		return new KrausDecomposition(List.of(k0, k1), 1);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof PhaseFlipChannel other))
			return false;
		return Double.compare(probability, other.probability) == 0 && java.util.Arrays.equals(qubits, other.qubits);
	}

	@Override
	public int hashCode() {
		return Objects.hash(probability, java.util.Arrays.hashCode(qubits));
	}

	@Override
	public String toString() {
		return "PhaseFlipChannel[p=" + probability + ", qubits=" + java.util.Arrays.toString(qubits) + "]";
	}
}
