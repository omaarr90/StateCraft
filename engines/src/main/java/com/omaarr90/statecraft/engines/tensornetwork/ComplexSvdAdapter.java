package com.omaarr90.statecraft.engines.tensornetwork;

import java.util.Objects;

interface ComplexSvdAdapter {

	SvdSplit split(int rows, int cols, double[] matrixData, int maxRank, double singularCutoff);

	record SvdSplit(int rows, int cols, int rank, double[] leftFactor, double[] rightFactor, double discardedWeight) {

		public SvdSplit {
			if (rows <= 0) {
				throw new IllegalArgumentException("rows must be positive");
			}
			if (cols <= 0) {
				throw new IllegalArgumentException("cols must be positive");
			}
			if (rank <= 0 || rank > Math.min(rows, cols)) {
				throw new IllegalArgumentException("rank out of range: " + rank);
			}
			leftFactor = Objects.requireNonNull(leftFactor, "leftFactor");
			rightFactor = Objects.requireNonNull(rightFactor, "rightFactor");
			int expectedLeftLength = rows * rank * 2;
			if (leftFactor.length != expectedLeftLength) {
				throw new IllegalArgumentException(
						"leftFactor length mismatch: expected " + expectedLeftLength + ", got " + leftFactor.length);
			}
			int expectedRightLength = rank * cols * 2;
			if (rightFactor.length != expectedRightLength) {
				throw new IllegalArgumentException(
						"rightFactor length mismatch: expected " + expectedRightLength + ", got " + rightFactor.length);
			}
			if (!Double.isFinite(discardedWeight) || discardedWeight < 0.0) {
				throw new IllegalArgumentException("discardedWeight must be finite and non-negative");
			}
		}
	}
}
