package com.omaarr90.statecraft.engines.tensornetwork;

import com.omaarr90.statecraft.core.math.ComplexNumber;

final class TensorNetworkMatrices {

	static final double[] CNOT = new double[]{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0,
			0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0};

	static final double[] SWAP = new double[]{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0,
			0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0};

	private static final int[] SWAP_PERMUTATION = new int[]{0, 2, 1, 3};

	private TensorNetworkMatrices() {
	}

	static double[] diagonalToMatrix(ComplexNumber[] diagonal) {
		if (diagonal.length != 4) {
			throw new IllegalArgumentException("two-qubit diagonal must contain 4 elements");
		}
		double[] matrix = new double[32];
		for (int index = 0; index < diagonal.length; index++) {
			int matrixIndex = complexIndex(index, index, 4);
			matrix[matrixIndex] = diagonal[index].real();
			matrix[matrixIndex + 1] = diagonal[index].imag();
		}
		return matrix;
	}

	static double[] swapOperandOrder(double[] matrix) {
		if (matrix.length != 32) {
			throw new IllegalArgumentException("two-qubit matrix must contain 32 doubles");
		}
		double[] swapped = new double[matrix.length];
		for (int row = 0; row < 4; row++) {
			for (int col = 0; col < 4; col++) {
				int srcRow = SWAP_PERMUTATION[row];
				int srcCol = SWAP_PERMUTATION[col];
				int src = complexIndex(srcRow, srcCol, 4);
				int dst = complexIndex(row, col, 4);
				swapped[dst] = matrix[src];
				swapped[dst + 1] = matrix[src + 1];
			}
		}
		return swapped;
	}

	private static int complexIndex(int row, int col, int cols) {
		return ((row * cols) + col) << 1;
	}
}
