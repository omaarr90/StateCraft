package com.omaarr90.statecraft.core.noise;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.List;
import java.util.Objects;

/**
 * Kraus decomposition of a quantum channel.
 * <p>
 * A quantum channel E maps density matrices as: E(ρ) = Σ_i K_i ρ K_i† where the
 * Kraus operators satisfy the completeness relation: Σ_i K_i† K_i = I
 * <p>
 * For Monte Carlo simulation, simulators derive branch probabilities from the
 * current state and these operators rather than storing static sampling
 * weights.
 *
 * @param operators
 *            list of Kraus operators
 * @param numQubits
 *            number of qubits this channel acts on
 */
public record KrausDecomposition(List<KrausOperator> operators, int numQubits) {

	private static final double COMPLETENESS_TOLERANCE = 1e-10;

	public KrausDecomposition {
		Objects.requireNonNull(operators, "operators");
		if (operators.isEmpty()) {
			throw new IllegalArgumentException("operators must not be empty");
		}
		if (numQubits < 1) {
			throw new IllegalArgumentException("numQubits must be at least 1");
		}

		// Defensive copy
		operators = List.copyOf(operators);

		// Verify all operators have consistent dimension
		int expectedDim = 1 << numQubits;
		for (KrausOperator op : operators) {
			if (op.dimension() != expectedDim) {
				throw new IllegalArgumentException(
						"all operators must have dimension " + expectedDim + ", got " + op.dimension());
			}
		}
	}

	/**
	 * Validates the completeness relation: Σ_i K_i† K_i = I
	 * <p>
	 * This is more expensive than the constructor's dimension validation and is
	 * typically only needed during testing or when constructing custom channels.
	 *
	 * @throws IllegalStateException
	 *             if the completeness relation is violated
	 */
	public void validateCompleteness() {
		int dim = 1 << numQubits;
		ComplexNumber[] sum = new ComplexNumber[dim * dim];
		for (int i = 0; i < sum.length; i++) {
			sum[i] = ComplexNumber.zero();
		}

		// Compute Σ_i K_i† K_i
		for (KrausOperator op : operators) {
			ComplexNumber[] kDagger = conjugateTranspose(op.matrix(), dim);
			ComplexNumber[] product = matrixMultiply(kDagger, op.matrix(), dim);
			for (int i = 0; i < sum.length; i++) {
				sum[i] = sum[i].plus(product[i]);
			}
		}

		// Verify sum equals identity
		for (int row = 0; row < dim; row++) {
			for (int col = 0; col < dim; col++) {
				ComplexNumber expected = (row == col) ? ComplexNumber.one() : ComplexNumber.zero();
				ComplexNumber actual = sum[row * dim + col];
				double realDiff = Math.abs(expected.real() - actual.real());
				double imagDiff = Math.abs(expected.imag() - actual.imag());
				if (realDiff > COMPLETENESS_TOLERANCE || imagDiff > COMPLETENESS_TOLERANCE) {
					throw new IllegalStateException("completeness relation violated at (" + row + "," + col
							+ "): expected " + expected + ", got " + actual);
				}
			}
		}
	}

	private ComplexNumber[] conjugateTranspose(ComplexNumber[] matrix, int dim) {
		ComplexNumber[] result = new ComplexNumber[dim * dim];
		for (int row = 0; row < dim; row++) {
			for (int col = 0; col < dim; col++) {
				result[col * dim + row] = matrix[row * dim + col].conjugate();
			}
		}
		return result;
	}

	private ComplexNumber[] matrixMultiply(ComplexNumber[] a, ComplexNumber[] b, int dim) {
		ComplexNumber[] result = new ComplexNumber[dim * dim];
		for (int row = 0; row < dim; row++) {
			for (int col = 0; col < dim; col++) {
				ComplexNumber sum = ComplexNumber.zero();
				for (int k = 0; k < dim; k++) {
					sum = sum.plus(a[row * dim + k].times(b[k * dim + col]));
				}
				result[row * dim + col] = sum;
			}
		}
		return result;
	}
}
