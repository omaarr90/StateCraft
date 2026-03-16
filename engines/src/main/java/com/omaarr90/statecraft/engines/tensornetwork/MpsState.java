package com.omaarr90.statecraft.engines.tensornetwork;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.quantum.SingleQubitGate;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.Arrays;
import org.ejml.data.ZMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_ZDRM;
import org.ejml.interfaces.decomposition.QRDecomposition;

final class MpsState {

	private final int qubitCount;
	private final ComplexSvdAdapter svdAdapter;
	private final int maxBondDimension;
	private final double singularCutoff;
	private final MpsTensor[] tensors;
	private final int[] logicalToPhysical;
	private final int[] physicalToLogical;

	private double totalDiscardedWeight;
	private int maxObservedBondDimension;

	private MpsState(int qubitCount, ComplexSvdAdapter svdAdapter, int maxBondDimension, double singularCutoff,
			MpsTensor[] tensors) {
		this.qubitCount = qubitCount;
		this.svdAdapter = svdAdapter;
		this.maxBondDimension = maxBondDimension;
		this.singularCutoff = singularCutoff;
		this.tensors = tensors;
		this.logicalToPhysical = new int[qubitCount];
		this.physicalToLogical = new int[qubitCount];
		for (int index = 0; index < qubitCount; index++) {
			logicalToPhysical[index] = index;
			physicalToLogical[index] = index;
			maxObservedBondDimension = Math.max(maxObservedBondDimension, tensors[index].leftDim);
			maxObservedBondDimension = Math.max(maxObservedBondDimension, tensors[index].rightDim);
		}
	}

	static MpsState zeroState(int qubitCount, ComplexSvdAdapter svdAdapter, int maxBondDimension,
			double singularCutoff) {
		MpsTensor[] tensors = new MpsTensor[qubitCount];
		for (int qubit = 0; qubit < qubitCount; qubit++) {
			tensors[qubit] = MpsTensor.basisState(0);
		}
		return new MpsState(qubitCount, svdAdapter, maxBondDimension, singularCutoff, tensors);
	}

	static MpsState basisState(int qubitCount, int[] oneQubits, ComplexSvdAdapter svdAdapter, int maxBondDimension,
			double singularCutoff) {
		boolean[] ones = new boolean[qubitCount];
		for (int qubit : oneQubits) {
			if (qubit < 0 || qubit >= qubitCount) {
				throw new IllegalArgumentException("basis-state qubit out of range: " + qubit);
			}
			ones[qubit] = true;
		}
		MpsTensor[] tensors = new MpsTensor[qubitCount];
		for (int qubit = 0; qubit < qubitCount; qubit++) {
			tensors[qubit] = MpsTensor.basisState(ones[qubit] ? 1 : 0);
		}
		return new MpsState(qubitCount, svdAdapter, maxBondDimension, singularCutoff, tensors);
	}

	static MpsState fromStateVector(StateVector stateVector, ComplexSvdAdapter svdAdapter, int maxBondDimension,
			double singularCutoff) {
		int qubitCount = stateVector.qubitCount();
		int rows = 1;
		int cols = 1 << qubitCount;
		double[] current = stateVector.copyData();
		MpsTensor[] tensors = new MpsTensor[qubitCount];
		double discardedWeight = 0.0;
		int maxBondSeen = 1;

		for (int site = 0; site < qubitCount; site++) {
			int rightColumns = cols >> 1;
			int matrixRows = rows << 1;
			double[] matrix = new double[matrixRows * rightColumns * 2];

			for (int left = 0; left < rows; left++) {
				for (int column = 0; column < cols; column++) {
					int bit = column & 1;
					int reducedColumn = column >>> 1;
					int source = complexIndex(left, column, cols);
					int targetRow = (left << 1) | bit;
					int target = complexIndex(targetRow, reducedColumn, rightColumns);
					matrix[target] = current[source];
					matrix[target + 1] = current[source + 1];
				}
			}

			ComplexSvdAdapter.SvdSplit split = svdAdapter.split(matrixRows, rightColumns, matrix, maxBondDimension,
					singularCutoff);
			tensors[site] = leftFactorToTensor(rows, split.rank(), split.leftFactor());
			current = split.rightFactor();
			rows = split.rank();
			cols = rightColumns;
			discardedWeight += split.discardedWeight();
			maxBondSeen = Math.max(maxBondSeen, rows);
		}

		MpsState state = new MpsState(qubitCount, svdAdapter, maxBondDimension, singularCutoff, tensors);
		state.totalDiscardedWeight = discardedWeight;
		state.maxObservedBondDimension = Math.max(state.maxObservedBondDimension, maxBondSeen);
		return state;
	}

	void applySingleGate(int logicalQubit, SingleQubitGate gate) {
		int physicalQubit = physicalOfLogical(logicalQubit);
		MpsTensor tensor = tensors[physicalQubit];

		double g00r = gate.element(0, 0).real();
		double g00i = gate.element(0, 0).imag();
		double g01r = gate.element(0, 1).real();
		double g01i = gate.element(0, 1).imag();
		double g10r = gate.element(1, 0).real();
		double g10i = gate.element(1, 0).imag();
		double g11r = gate.element(1, 1).real();
		double g11i = gate.element(1, 1).imag();

		for (int left = 0; left < tensor.leftDim; left++) {
			for (int right = 0; right < tensor.rightDim; right++) {
				double a0r = tensor.real(left, 0, right);
				double a0i = tensor.imag(left, 0, right);
				double a1r = tensor.real(left, 1, right);
				double a1i = tensor.imag(left, 1, right);

				double new0r = (g00r * a0r - g00i * a0i) + (g01r * a1r - g01i * a1i);
				double new0i = (g00r * a0i + g00i * a0r) + (g01r * a1i + g01i * a1r);
				double new1r = (g10r * a0r - g10i * a0i) + (g11r * a1r - g11i * a1i);
				double new1i = (g10r * a0i + g10i * a0r) + (g11r * a1i + g11i * a1r);

				tensor.set(left, 0, right, new0r, new0i);
				tensor.set(left, 1, right, new1r, new1i);
			}
		}
	}

	void applyCnot(int controlLogicalQubit, int targetLogicalQubit) {
		applyTwoQubitOperation(controlLogicalQubit, targetLogicalQubit, TensorNetworkMatrices.CNOT);
	}

	void applyDiagonal(int firstLogicalQubit, int secondLogicalQubit, ComplexNumber[] diagonal) {
		applyTwoQubitOperation(firstLogicalQubit, secondLogicalQubit, TensorNetworkMatrices.diagonalToMatrix(diagonal));
	}

	void applySwapGate(int firstLogicalQubit, int secondLogicalQubit) {
		applyTwoQubitOperation(firstLogicalQubit, secondLogicalQubit, TensorNetworkMatrices.SWAP);
	}

	StateVector toStateVectorLogicalOrder() {
		int dimension = 1 << qubitCount;
		double[] data = new double[dimension << 1];
		for (int logicalBasis = 0; logicalBasis < dimension; logicalBasis++) {
			int physicalBasis = toPhysicalBasisIndex(logicalBasis);
			double[] amplitude = amplitudeOfPhysicalBasis(physicalBasis);
			int target = logicalBasis << 1;
			data[target] = amplitude[0];
			data[target + 1] = amplitude[1];
		}
		return StateVector.fromArray(qubitCount, data);
	}

	int qubitCount() {
		return qubitCount;
	}

	int physicalOfLogical(int logicalQubit) {
		return logicalToPhysical[logicalQubit];
	}

	MpsTensor tensorAtPhysical(int physicalQubit) {
		return tensors[physicalQubit];
	}

	double totalDiscardedWeight() {
		return totalDiscardedWeight;
	}

	int maxObservedBondDimension() {
		return maxObservedBondDimension;
	}

	private void applyTwoQubitOperation(int firstLogicalQubit, int secondLogicalQubit, double[] matrix) {
		int firstPhysical = physicalOfLogical(firstLogicalQubit);
		int secondPhysical = physicalOfLogical(secondLogicalQubit);
		while (Math.abs(firstPhysical - secondPhysical) > 1) {
			if (firstPhysical < secondPhysical) {
				applyAdjacentSwapForRouting(firstPhysical);
				firstPhysical++;
			} else {
				applyAdjacentSwapForRouting(firstPhysical - 1);
				firstPhysical--;
			}
			secondPhysical = physicalOfLogical(secondLogicalQubit);
		}

		int leftPhysical = Math.min(firstPhysical, secondPhysical);
		boolean firstIsLeft = firstPhysical < secondPhysical;
		double[] oriented = firstIsLeft ? matrix : TensorNetworkMatrices.swapOperandOrder(matrix);
		applyAdjacentTwoQubitMatrix(leftPhysical, oriented);
	}

	private void applyAdjacentSwapForRouting(int leftPhysical) {
		applyAdjacentTwoQubitMatrix(leftPhysical, TensorNetworkMatrices.SWAP);
		swapLogicalMapping(leftPhysical, leftPhysical + 1);
	}

	private void swapLogicalMapping(int firstPhysical, int secondPhysical) {
		int firstLogical = physicalToLogical[firstPhysical];
		int secondLogical = physicalToLogical[secondPhysical];
		physicalToLogical[firstPhysical] = secondLogical;
		physicalToLogical[secondPhysical] = firstLogical;
		logicalToPhysical[firstLogical] = secondPhysical;
		logicalToPhysical[secondLogical] = firstPhysical;
	}

	private void applyAdjacentTwoQubitMatrix(int leftPhysical, double[] matrix) {
		MpsTensor leftTensor = tensors[leftPhysical];
		MpsTensor rightTensor = tensors[leftPhysical + 1];
		int leftDim = leftTensor.leftDim;
		int sharedDim = leftTensor.rightDim;
		int rightDim = rightTensor.rightDim;
		if (sharedDim != rightTensor.leftDim) {
			throw new IllegalStateException("adjacent tensor bond mismatch");
		}

		int rows = leftDim << 1;
		int cols = rightDim << 1;
		double[] merged = new double[rows * cols * 2];

		for (int left = 0; left < leftDim; left++) {
			for (int right = 0; right < rightDim; right++) {
				double[] amplitudes = contractAdjacentBlock(leftTensor, rightTensor, left, right);
				double[] transformed = applyTwoQubitMatrix(matrix, amplitudes);

				for (int bitLeft = 0; bitLeft < 2; bitLeft++) {
					for (int bitRight = 0; bitRight < 2; bitRight++) {
						int row = (left << 1) | bitLeft;
						int col = (bitRight * rightDim) + right;
						int matrixIndex = complexIndex(row, col, cols);
						int ampIndex = complexIndex(bitLeft, bitRight, 2);
						merged[matrixIndex] = transformed[ampIndex];
						merged[matrixIndex + 1] = transformed[ampIndex + 1];
					}
				}
			}
		}

		int fullRank = Math.min(rows, cols);
		ComplexSvdAdapter.SvdSplit split;
		if (maxBondDimension >= fullRank) {
			try {
				split = qrSplit(rows, cols, merged, singularCutoff);
			} catch (IllegalStateException ignored) {
				split = directSplit(rows, cols, merged);
			}
		} else {
			try {
				split = svdAdapter.split(rows, cols, merged, maxBondDimension, singularCutoff);
			} catch (IllegalStateException ignored) {
				split = fallbackTruncatedSplit(rows, cols, merged, maxBondDimension);
			}
		}
		tensors[leftPhysical] = leftFactorToTensor(leftDim, split.rank(), split.leftFactor());
		tensors[leftPhysical + 1] = rightFactorToTensor(split.rank(), rightDim, split.rightFactor());
		totalDiscardedWeight += split.discardedWeight();
		maxObservedBondDimension = Math.max(maxObservedBondDimension, split.rank());
	}

	private static ComplexSvdAdapter.SvdSplit qrSplit(int rows, int cols, double[] matrix, double cutoff) {
		ZMatrixRMaj source = new ZMatrixRMaj(rows, cols);
		System.arraycopy(matrix, 0, source.data, 0, matrix.length);
		if (rows >= cols) {
			QRDecomposition<ZMatrixRMaj> decomposition = DecompositionFactory_ZDRM.qr(rows, cols);
			if (!decomposition.decompose(source)) {
				throw new IllegalStateException("complex QR decomposition failed");
			}
			ZMatrixRMaj qCompact = decomposition.getQ(null, true);
			ZMatrixRMaj rCompact = decomposition.getR(null, true);
			int fullRank = cols;
			int rank = effectiveRank(rCompact, fullRank, cutoff);

			double[] leftFactor = new double[rows * rank * 2];
			for (int row = 0; row < rows; row++) {
				for (int col = 0; col < rank; col++) {
					int sourceIndex = complexIndex(row, col, qCompact.numCols);
					int targetIndex = complexIndex(row, col, rank);
					leftFactor[targetIndex] = qCompact.data[sourceIndex];
					leftFactor[targetIndex + 1] = qCompact.data[sourceIndex + 1];
				}
			}

			double[] rightFactor = new double[rank * cols * 2];
			for (int row = 0; row < rank; row++) {
				for (int col = row; col < cols; col++) {
					int sourceIndex = complexIndex(row, col, rCompact.numCols);
					int targetIndex = complexIndex(row, col, cols);
					rightFactor[targetIndex] = rCompact.data[sourceIndex];
					rightFactor[targetIndex + 1] = rCompact.data[sourceIndex + 1];
				}
			}
			return new ComplexSvdAdapter.SvdSplit(rows, cols, rank, leftFactor, rightFactor, 0.0);
		}

		ZMatrixRMaj conjugateTranspose = conjugateTranspose(source);
		QRDecomposition<ZMatrixRMaj> decomposition = DecompositionFactory_ZDRM.qr(cols, rows);
		if (!decomposition.decompose(conjugateTranspose)) {
			throw new IllegalStateException("complex QR decomposition failed");
		}
		ZMatrixRMaj qCompact = decomposition.getQ(null, true); // (cols x rows)
		ZMatrixRMaj rCompact = decomposition.getR(null, true); // (rows x rows)
		int fullRank = rows;
		int rank = effectiveRank(rCompact, fullRank, cutoff);

		double[] leftFactor = new double[rows * rank * 2];
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < rank; col++) {
				int sourceIndex = complexIndex(col, row, rCompact.numCols);
				int targetIndex = complexIndex(row, col, rank);
				leftFactor[targetIndex] = rCompact.data[sourceIndex];
				leftFactor[targetIndex + 1] = -rCompact.data[sourceIndex + 1];
			}
		}

		double[] rightFactor = new double[rank * cols * 2];
		for (int row = 0; row < rank; row++) {
			for (int col = 0; col < cols; col++) {
				int sourceIndex = complexIndex(col, row, qCompact.numCols);
				int targetIndex = complexIndex(row, col, cols);
				rightFactor[targetIndex] = qCompact.data[sourceIndex];
				rightFactor[targetIndex + 1] = -qCompact.data[sourceIndex + 1];
			}
		}

		return new ComplexSvdAdapter.SvdSplit(rows, cols, rank, leftFactor, rightFactor, 0.0);
	}

	private static ComplexSvdAdapter.SvdSplit directSplit(int rows, int cols, double[] matrix) {
		if (rows <= cols) {
			int rank = rows;
			double[] leftFactor = new double[rows * rank * 2];
			for (int index = 0; index < rank; index++) {
				int diagonal = complexIndex(index, index, rank);
				leftFactor[diagonal] = 1.0;
			}
			double[] rightFactor = matrix.clone();
			return new ComplexSvdAdapter.SvdSplit(rows, cols, rank, leftFactor, rightFactor, 0.0);
		}
		int rank = cols;
		double[] leftFactor = matrix.clone();
		double[] rightFactor = new double[rank * cols * 2];
		for (int index = 0; index < rank; index++) {
			int diagonal = complexIndex(index, index, cols);
			rightFactor[diagonal] = 1.0;
		}
		return new ComplexSvdAdapter.SvdSplit(rows, cols, rank, leftFactor, rightFactor, 0.0);
	}

	private static ComplexSvdAdapter.SvdSplit fallbackTruncatedSplit(int rows, int cols, double[] matrix, int maxRank) {
		int rank = Math.min(Math.min(rows, cols), maxRank);
		if (rank <= 0) {
			throw new IllegalArgumentException("maxRank must be positive");
		}

		double totalNormSq = frobeniusNormSq(matrix);
		if (rows <= cols) {
			double[] leftFactor = new double[rows * rank * 2];
			for (int diagonal = 0; diagonal < rank; diagonal++) {
				int index = complexIndex(diagonal, diagonal, rank);
				leftFactor[index] = 1.0;
			}
			double[] rightFactor = new double[rank * cols * 2];
			for (int row = 0; row < rank; row++) {
				for (int col = 0; col < cols; col++) {
					int source = complexIndex(row, col, cols);
					int target = complexIndex(row, col, cols);
					rightFactor[target] = matrix[source];
					rightFactor[target + 1] = matrix[source + 1];
				}
			}
			double keptNormSq = frobeniusNormSq(rightFactor);
			normalizeInPlace(rightFactor, keptNormSq);
			double discarded = Math.max(0.0, totalNormSq - keptNormSq);
			return new ComplexSvdAdapter.SvdSplit(rows, cols, rank, leftFactor, rightFactor, discarded);
		}

		double[] leftFactor = new double[rows * rank * 2];
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < rank; col++) {
				int source = complexIndex(row, col, cols);
				int target = complexIndex(row, col, rank);
				leftFactor[target] = matrix[source];
				leftFactor[target + 1] = matrix[source + 1];
			}
		}
		double[] rightFactor = new double[rank * cols * 2];
		for (int diagonal = 0; diagonal < rank; diagonal++) {
			int index = complexIndex(diagonal, diagonal, cols);
			rightFactor[index] = 1.0;
		}
		double keptNormSq = frobeniusNormSq(leftFactor);
		normalizeInPlace(leftFactor, keptNormSq);
		double discarded = Math.max(0.0, totalNormSq - keptNormSq);
		return new ComplexSvdAdapter.SvdSplit(rows, cols, rank, leftFactor, rightFactor, discarded);
	}

	private static double frobeniusNormSq(double[] matrix) {
		double normSq = 0.0;
		for (int index = 0; index < matrix.length; index += 2) {
			double real = matrix[index];
			double imag = matrix[index + 1];
			normSq += (real * real) + (imag * imag);
		}
		return normSq;
	}

	private static void normalizeInPlace(double[] matrix, double normSq) {
		if (normSq <= 0.0) {
			return;
		}
		double scale = 1.0 / Math.sqrt(normSq);
		for (int index = 0; index < matrix.length; index++) {
			matrix[index] *= scale;
		}
	}

	private static int effectiveRank(ZMatrixRMaj upperTriangular, int fullRank, double cutoff) {
		if (cutoff <= 0.0) {
			return fullRank;
		}
		int effective = 0;
		for (int index = 0; index < fullRank; index++) {
			int diagonal = complexIndex(index, index, upperTriangular.numCols);
			double real = upperTriangular.data[diagonal];
			double imag = upperTriangular.data[diagonal + 1];
			if (Math.hypot(real, imag) > cutoff) {
				effective++;
			}
		}
		return Math.max(1, effective);
	}

	private static ZMatrixRMaj conjugateTranspose(ZMatrixRMaj matrix) {
		ZMatrixRMaj out = new ZMatrixRMaj(matrix.numCols, matrix.numRows);
		for (int row = 0; row < matrix.numRows; row++) {
			for (int col = 0; col < matrix.numCols; col++) {
				int source = complexIndex(row, col, matrix.numCols);
				int target = complexIndex(col, row, out.numCols);
				out.data[target] = matrix.data[source];
				out.data[target + 1] = -matrix.data[source + 1];
			}
		}
		return out;
	}

	private static MpsTensor leftFactorToTensor(int leftDim, int rank, double[] leftFactor) {
		double[] data = new double[leftDim * MpsTensor.PHYSICAL_DIM * rank * 2];
		MpsTensor tensor = new MpsTensor(leftDim, rank, data);
		for (int left = 0; left < leftDim; left++) {
			for (int bit = 0; bit < 2; bit++) {
				int row = (left << 1) | bit;
				for (int k = 0; k < rank; k++) {
					int source = complexIndex(row, k, rank);
					tensor.set(left, bit, k, leftFactor[source], leftFactor[source + 1]);
				}
			}
		}
		return tensor;
	}

	private static MpsTensor rightFactorToTensor(int rank, int rightDim, double[] rightFactor) {
		double[] data = new double[rank * MpsTensor.PHYSICAL_DIM * rightDim * 2];
		MpsTensor tensor = new MpsTensor(rank, rightDim, data);
		int cols = rightDim << 1;
		for (int k = 0; k < rank; k++) {
			for (int bit = 0; bit < 2; bit++) {
				for (int right = 0; right < rightDim; right++) {
					int col = (bit * rightDim) + right;
					int source = complexIndex(k, col, cols);
					tensor.set(k, bit, right, rightFactor[source], rightFactor[source + 1]);
				}
			}
		}
		return tensor;
	}

	private static double[] contractAdjacentBlock(MpsTensor leftTensor, MpsTensor rightTensor, int left, int right) {
		double[] amplitudes = new double[8];
		for (int middle = 0; middle < leftTensor.rightDim; middle++) {
			for (int bitLeft = 0; bitLeft < 2; bitLeft++) {
				double ar = leftTensor.real(left, bitLeft, middle);
				double ai = leftTensor.imag(left, bitLeft, middle);
				for (int bitRight = 0; bitRight < 2; bitRight++) {
					double br = rightTensor.real(middle, bitRight, right);
					double bi = rightTensor.imag(middle, bitRight, right);
					int index = complexIndex(bitLeft, bitRight, 2);
					amplitudes[index] += (ar * br) - (ai * bi);
					amplitudes[index + 1] += (ar * bi) + (ai * br);
				}
			}
		}
		return amplitudes;
	}

	private static double[] applyTwoQubitMatrix(double[] matrix, double[] amplitudes) {
		double[] out = new double[8];
		for (int row = 0; row < 4; row++) {
			double sumReal = 0.0;
			double sumImag = 0.0;
			for (int col = 0; col < 4; col++) {
				int matrixIndex = complexIndex(row, col, 4);
				int ampIndex = col << 1;
				double mr = matrix[matrixIndex];
				double mi = matrix[matrixIndex + 1];
				double ar = amplitudes[ampIndex];
				double ai = amplitudes[ampIndex + 1];
				sumReal += (mr * ar) - (mi * ai);
				sumImag += (mr * ai) + (mi * ar);
			}
			out[row << 1] = sumReal;
			out[(row << 1) + 1] = sumImag;
		}
		return out;
	}

	private double[] amplitudeOfPhysicalBasis(int physicalBasis) {
		double[] vector = new double[]{1.0, 0.0};
		int vectorDim = 1;
		for (int physical = 0; physical < qubitCount; physical++) {
			MpsTensor tensor = tensors[physical];
			int bit = (physicalBasis >>> physical) & 1;
			double[] next = new double[tensor.rightDim << 1];
			for (int left = 0; left < vectorDim; left++) {
				double vr = vector[left << 1];
				double vi = vector[(left << 1) + 1];
				for (int right = 0; right < tensor.rightDim; right++) {
					double ar = tensor.real(left, bit, right);
					double ai = tensor.imag(left, bit, right);
					int target = right << 1;
					next[target] += (vr * ar) - (vi * ai);
					next[target + 1] += (vr * ai) + (vi * ar);
				}
			}
			vector = next;
			vectorDim = tensor.rightDim;
		}
		return Arrays.copyOf(vector, 2);
	}

	private int toPhysicalBasisIndex(int logicalBasisIndex) {
		int physicalBasis = 0;
		for (int physical = 0; physical < qubitCount; physical++) {
			int logical = physicalToLogical[physical];
			if (((logicalBasisIndex >>> logical) & 1) != 0) {
				physicalBasis |= 1 << physical;
			}
		}
		return physicalBasis;
	}

	private static int complexIndex(int row, int col, int cols) {
		return ((row * cols) + col) << 1;
	}
}
