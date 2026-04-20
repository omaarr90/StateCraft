package com.omaarr90.statecraft.core.noise;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.Objects;

/**
 * A Kraus operator representing a quantum noise operation.
 * <p>
 * In the Kraus representation of quantum channels, a noise operation is
 * described by a set of operators {K_i} such that the density matrix transforms
 * as: ρ' = Σ_i K_i ρ K_i†
 * <p>
 * For Monte Carlo simulation with pure states, a simulator samples one Kraus
 * operator using state-dependent branch probabilities p_i = ||K_i|ψ⟩||².
 *
 * @param matrix
 *            flattened operator matrix in row-major order (length = n² for
 *            n-dimensional operator)
 */
public record KrausOperator(ComplexNumber[] matrix) {

	public KrausOperator {
		Objects.requireNonNull(matrix, "matrix");
		if (matrix.length == 0) {
			throw new IllegalArgumentException("matrix must not be empty");
		}
		// Verify matrix is square
		int dim = (int) Math.sqrt(matrix.length);
		if (dim * dim != matrix.length) {
			throw new IllegalArgumentException("matrix length must be a perfect square, got " + matrix.length);
		}
		// Defensive copy
		matrix = matrix.clone();
	}

	/**
	 * Returns the dimension of this operator (e.g., 2 for single-qubit, 4 for
	 * two-qubit).
	 */
	public int dimension() {
		return (int) Math.sqrt(matrix.length);
	}

	/**
	 * Returns the number of qubits this operator acts on.
	 */
	public int numQubits() {
		int dim = dimension();
		return Integer.numberOfTrailingZeros(dim);
	}
}
