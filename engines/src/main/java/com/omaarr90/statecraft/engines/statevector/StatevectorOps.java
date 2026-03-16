package com.omaarr90.statecraft.engines.statevector;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorSpecies;

public final class StatevectorOps {

	private static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;
	private static final int COMPLEX_PER_VECTOR = SPECIES.length() >= 2 ? SPECIES.length() >> 1 : 0;
	private static final boolean VECTOR_COMPLEX_AVAILABLE = COMPLEX_PER_VECTOR > 0;
	private static final VectorShuffle<Double> SWAP_SHUFFLE = VECTOR_COMPLEX_AVAILABLE ? buildSwapShuffle() : null;
	private static final DoubleVector SIGN_VECTOR = VECTOR_COMPLEX_AVAILABLE ? buildSignVector() : null;

	private StatevectorOps() {
	}

	public static void applySingleGate(double[] state, int target, double g00r, double g00i, double g01r, double g01i,
			double g10r, double g10i, double g11r, double g11i) {
		int dimension = state.length >> 1;
		int stride = 1 << target;
		int period = stride << 1;

		DoubleVector g00rVec = null;
		DoubleVector g00iVec = null;
		DoubleVector g01rVec = null;
		DoubleVector g01iVec = null;
		DoubleVector g10rVec = null;
		DoubleVector g10iVec = null;
		DoubleVector g11rVec = null;
		DoubleVector g11iVec = null;
		if (VECTOR_COMPLEX_AVAILABLE) {
			g00rVec = DoubleVector.broadcast(SPECIES, g00r);
			g00iVec = DoubleVector.broadcast(SPECIES, g00i);
			g01rVec = DoubleVector.broadcast(SPECIES, g01r);
			g01iVec = DoubleVector.broadcast(SPECIES, g01i);
			g10rVec = DoubleVector.broadcast(SPECIES, g10r);
			g10iVec = DoubleVector.broadcast(SPECIES, g10i);
			g11rVec = DoubleVector.broadcast(SPECIES, g11r);
			g11iVec = DoubleVector.broadcast(SPECIES, g11i);
		}

		for (int base = 0; base < dimension; base += period) {
			int offset = 0;
			int vectorLimit = VECTOR_COMPLEX_AVAILABLE ? stride - (stride % COMPLEX_PER_VECTOR) : 0;
			for (; offset < vectorLimit; offset += COMPLEX_PER_VECTOR) {
				int idx0 = base + offset;
				int idx1 = idx0 + stride;
				int off0 = toDoubleIndex(idx0);
				int off1 = toDoubleIndex(idx1);

				DoubleVector a0 = DoubleVector.fromArray(SPECIES, state, off0);
				DoubleVector a1 = DoubleVector.fromArray(SPECIES, state, off1);

				DoubleVector new0 = complexMul(a0, g00rVec, g00iVec).add(complexMul(a1, g01rVec, g01iVec));
				DoubleVector new1 = complexMul(a0, g10rVec, g10iVec).add(complexMul(a1, g11rVec, g11iVec));

				new0.intoArray(state, off0);
				new1.intoArray(state, off1);
			}
			for (; offset < stride; offset++) {
				int idx0 = base + offset;
				int idx1 = idx0 + stride;
				int off0 = toDoubleIndex(idx0);
				int off1 = toDoubleIndex(idx1);

				double a0r = state[off0];
				double a0i = state[off0 + 1];
				double a1r = state[off1];
				double a1i = state[off1 + 1];

				double new0r = g00r * a0r - g00i * a0i + g01r * a1r - g01i * a1i;
				double new0i = g00r * a0i + g00i * a0r + g01r * a1i + g01i * a1r;
				double new1r = g10r * a0r - g10i * a0i + g11r * a1r - g11i * a1i;
				double new1i = g10r * a0i + g10i * a0r + g11r * a1i + g11i * a1r;

				state[off0] = new0r;
				state[off0 + 1] = new0i;
				state[off1] = new1r;
				state[off1 + 1] = new1i;
			}
		}
	}

	public static void applyCnot(double[] state, int control, int target) {
		int controlMask = 1 << control;
		int targetMask = 1 << target;
		int dimension = state.length >> 1;
		for (int index = 0; index < dimension; index++) {
			if ((index & controlMask) == controlMask && (index & targetMask) == 0) {
				int flipped = index | targetMask;
				int offIndex = toDoubleIndex(index);
				int offFlipped = toDoubleIndex(flipped);
				double tmpR = state[offIndex];
				double tmpI = state[offIndex + 1];
				state[offIndex] = state[offFlipped];
				state[offIndex + 1] = state[offFlipped + 1];
				state[offFlipped] = tmpR;
				state[offFlipped + 1] = tmpI;
			}
		}
	}

	public static void applyTwoQubitUnitary(double[] state, int first, int second, double[] matrixReal,
			double[] matrixImag) {
		int low = Math.min(first, second);
		int high = Math.max(first, second);
		boolean firstIsLow = first == low;
		int lowMask = 1 << low;
		int highMask = 1 << high;
		int offsetFirst = firstIsLow ? lowMask : highMask;
		int offsetSecond = firstIsLow ? highMask : lowMask;
		int offsetBoth = lowMask | highMask;
		int lowStride = lowMask;
		int highStride = 1 << high;
		int blockSize = highStride << 1;
		int betweenStep = lowMask << 1;
		int dimension = state.length >> 1;

		double m00r = matrixReal[0];
		double m00i = matrixImag[0];
		double m01r = matrixReal[1];
		double m01i = matrixImag[1];
		double m02r = matrixReal[2];
		double m02i = matrixImag[2];
		double m03r = matrixReal[3];
		double m03i = matrixImag[3];

		double m10r = matrixReal[4];
		double m10i = matrixImag[4];
		double m11r = matrixReal[5];
		double m11i = matrixImag[5];
		double m12r = matrixReal[6];
		double m12i = matrixImag[6];
		double m13r = matrixReal[7];
		double m13i = matrixImag[7];

		double m20r = matrixReal[8];
		double m20i = matrixImag[8];
		double m21r = matrixReal[9];
		double m21i = matrixImag[9];
		double m22r = matrixReal[10];
		double m22i = matrixImag[10];
		double m23r = matrixReal[11];
		double m23i = matrixImag[11];

		double m30r = matrixReal[12];
		double m30i = matrixImag[12];
		double m31r = matrixReal[13];
		double m31i = matrixImag[13];
		double m32r = matrixReal[14];
		double m32i = matrixImag[14];
		double m33r = matrixReal[15];
		double m33i = matrixImag[15];

		DoubleVector m00rVec = null;
		DoubleVector m00iVec = null;
		DoubleVector m01rVec = null;
		DoubleVector m01iVec = null;
		DoubleVector m02rVec = null;
		DoubleVector m02iVec = null;
		DoubleVector m03rVec = null;
		DoubleVector m03iVec = null;

		DoubleVector m10rVec = null;
		DoubleVector m10iVec = null;
		DoubleVector m11rVec = null;
		DoubleVector m11iVec = null;
		DoubleVector m12rVec = null;
		DoubleVector m12iVec = null;
		DoubleVector m13rVec = null;
		DoubleVector m13iVec = null;

		DoubleVector m20rVec = null;
		DoubleVector m20iVec = null;
		DoubleVector m21rVec = null;
		DoubleVector m21iVec = null;
		DoubleVector m22rVec = null;
		DoubleVector m22iVec = null;
		DoubleVector m23rVec = null;
		DoubleVector m23iVec = null;

		DoubleVector m30rVec = null;
		DoubleVector m30iVec = null;
		DoubleVector m31rVec = null;
		DoubleVector m31iVec = null;
		DoubleVector m32rVec = null;
		DoubleVector m32iVec = null;
		DoubleVector m33rVec = null;
		DoubleVector m33iVec = null;

		if (VECTOR_COMPLEX_AVAILABLE) {
			m00rVec = DoubleVector.broadcast(SPECIES, m00r);
			m00iVec = DoubleVector.broadcast(SPECIES, m00i);
			m01rVec = DoubleVector.broadcast(SPECIES, m01r);
			m01iVec = DoubleVector.broadcast(SPECIES, m01i);
			m02rVec = DoubleVector.broadcast(SPECIES, m02r);
			m02iVec = DoubleVector.broadcast(SPECIES, m02i);
			m03rVec = DoubleVector.broadcast(SPECIES, m03r);
			m03iVec = DoubleVector.broadcast(SPECIES, m03i);

			m10rVec = DoubleVector.broadcast(SPECIES, m10r);
			m10iVec = DoubleVector.broadcast(SPECIES, m10i);
			m11rVec = DoubleVector.broadcast(SPECIES, m11r);
			m11iVec = DoubleVector.broadcast(SPECIES, m11i);
			m12rVec = DoubleVector.broadcast(SPECIES, m12r);
			m12iVec = DoubleVector.broadcast(SPECIES, m12i);
			m13rVec = DoubleVector.broadcast(SPECIES, m13r);
			m13iVec = DoubleVector.broadcast(SPECIES, m13i);

			m20rVec = DoubleVector.broadcast(SPECIES, m20r);
			m20iVec = DoubleVector.broadcast(SPECIES, m20i);
			m21rVec = DoubleVector.broadcast(SPECIES, m21r);
			m21iVec = DoubleVector.broadcast(SPECIES, m21i);
			m22rVec = DoubleVector.broadcast(SPECIES, m22r);
			m22iVec = DoubleVector.broadcast(SPECIES, m22i);
			m23rVec = DoubleVector.broadcast(SPECIES, m23r);
			m23iVec = DoubleVector.broadcast(SPECIES, m23i);

			m30rVec = DoubleVector.broadcast(SPECIES, m30r);
			m30iVec = DoubleVector.broadcast(SPECIES, m30i);
			m31rVec = DoubleVector.broadcast(SPECIES, m31r);
			m31iVec = DoubleVector.broadcast(SPECIES, m31i);
			m32rVec = DoubleVector.broadcast(SPECIES, m32r);
			m32iVec = DoubleVector.broadcast(SPECIES, m32i);
			m33rVec = DoubleVector.broadcast(SPECIES, m33r);
			m33iVec = DoubleVector.broadcast(SPECIES, m33i);
		}

		for (int baseHigh = 0; baseHigh < dimension; baseHigh += blockSize) {
			for (int baseMid = 0; baseMid < highStride; baseMid += betweenStep) {
				int baseOffset = baseHigh + baseMid;
				int offset = 0;
				int vectorLimit = VECTOR_COMPLEX_AVAILABLE ? lowStride - (lowStride % COMPLEX_PER_VECTOR) : 0;
				for (; offset < vectorLimit; offset += COMPLEX_PER_VECTOR) {
					int baseIndex = baseOffset + offset;
					int idx01 = baseIndex + offsetSecond;
					int idx10 = baseIndex + offsetFirst;
					int idx11 = baseIndex + offsetBoth;

					int off00 = toDoubleIndex(baseIndex);
					int off01 = toDoubleIndex(idx01);
					int off10 = toDoubleIndex(idx10);
					int off11 = toDoubleIndex(idx11);

					DoubleVector a00 = DoubleVector.fromArray(SPECIES, state, off00);
					DoubleVector a01 = DoubleVector.fromArray(SPECIES, state, off01);
					DoubleVector a10 = DoubleVector.fromArray(SPECIES, state, off10);
					DoubleVector a11 = DoubleVector.fromArray(SPECIES, state, off11);

					DoubleVector new0 = complexMul(a00, m00rVec, m00iVec).add(complexMul(a01, m01rVec, m01iVec))
							.add(complexMul(a10, m02rVec, m02iVec)).add(complexMul(a11, m03rVec, m03iVec));
					DoubleVector new1 = complexMul(a00, m10rVec, m10iVec).add(complexMul(a01, m11rVec, m11iVec))
							.add(complexMul(a10, m12rVec, m12iVec)).add(complexMul(a11, m13rVec, m13iVec));
					DoubleVector new2 = complexMul(a00, m20rVec, m20iVec).add(complexMul(a01, m21rVec, m21iVec))
							.add(complexMul(a10, m22rVec, m22iVec)).add(complexMul(a11, m23rVec, m23iVec));
					DoubleVector new3 = complexMul(a00, m30rVec, m30iVec).add(complexMul(a01, m31rVec, m31iVec))
							.add(complexMul(a10, m32rVec, m32iVec)).add(complexMul(a11, m33rVec, m33iVec));

					new0.intoArray(state, off00);
					new1.intoArray(state, off01);
					new2.intoArray(state, off10);
					new3.intoArray(state, off11);
				}
				for (; offset < lowStride; offset++) {
					int baseIndex = baseOffset + offset;
					int idx01 = baseIndex + offsetSecond;
					int idx10 = baseIndex + offsetFirst;
					int idx11 = baseIndex + offsetBoth;

					int off00 = toDoubleIndex(baseIndex);
					int off01 = toDoubleIndex(idx01);
					int off10 = toDoubleIndex(idx10);
					int off11 = toDoubleIndex(idx11);

					double a00r = state[off00];
					double a00i = state[off00 + 1];
					double a01r = state[off01];
					double a01i = state[off01 + 1];
					double a10r = state[off10];
					double a10i = state[off10 + 1];
					double a11r = state[off11];
					double a11i = state[off11 + 1];

					double new00r = m00r * a00r - m00i * a00i + m01r * a01r - m01i * a01i + m02r * a10r - m02i * a10i
							+ m03r * a11r - m03i * a11i;
					double new00i = m00r * a00i + m00i * a00r + m01r * a01i + m01i * a01r + m02r * a10i + m02i * a10r
							+ m03r * a11i + m03i * a11r;

					double new01r = m10r * a00r - m10i * a00i + m11r * a01r - m11i * a01i + m12r * a10r - m12i * a10i
							+ m13r * a11r - m13i * a11i;
					double new01i = m10r * a00i + m10i * a00r + m11r * a01i + m11i * a01r + m12r * a10i + m12i * a10r
							+ m13r * a11i + m13i * a11r;

					double new10r = m20r * a00r - m20i * a00i + m21r * a01r - m21i * a01i + m22r * a10r - m22i * a10i
							+ m23r * a11r - m23i * a11i;
					double new10i = m20r * a00i + m20i * a00r + m21r * a01i + m21i * a01r + m22r * a10i + m22i * a10r
							+ m23r * a11i + m23i * a11r;

					double new11r = m30r * a00r - m30i * a00i + m31r * a01r - m31i * a01i + m32r * a10r - m32i * a10i
							+ m33r * a11r - m33i * a11i;
					double new11i = m30r * a00i + m30i * a00r + m31r * a01i + m31i * a01r + m32r * a10i + m32i * a10r
							+ m33r * a11i + m33i * a11r;

					state[off00] = new00r;
					state[off00 + 1] = new00i;
					state[off01] = new01r;
					state[off01 + 1] = new01i;
					state[off10] = new10r;
					state[off10 + 1] = new10i;
					state[off11] = new11r;
					state[off11 + 1] = new11i;
				}
			}
		}
	}

	public static void applyTwoQubitDiagonal(double[] state, int first, int second, double[] diagonalReal,
			double[] diagonalImag) {
		int low = Math.min(first, second);
		int high = Math.max(first, second);
		boolean firstIsLow = first == low;
		int lowMask = 1 << low;
		int highMask = 1 << high;
		int offsetFirst = firstIsLow ? lowMask : highMask;
		int offsetSecond = firstIsLow ? highMask : lowMask;
		int offsetBoth = lowMask | highMask;
		int lowStride = lowMask;
		int highStride = 1 << high;
		int blockSize = highStride << 1;
		int betweenStep = lowMask << 1;
		int dimension = state.length >> 1;

		double d00r = diagonalReal[0];
		double d00i = diagonalImag[0];
		double d01r = diagonalReal[1];
		double d01i = diagonalImag[1];
		double d10r = diagonalReal[2];
		double d10i = diagonalImag[2];
		double d11r = diagonalReal[3];
		double d11i = diagonalImag[3];

		DoubleVector d00rVec = null;
		DoubleVector d00iVec = null;
		DoubleVector d01rVec = null;
		DoubleVector d01iVec = null;
		DoubleVector d10rVec = null;
		DoubleVector d10iVec = null;
		DoubleVector d11rVec = null;
		DoubleVector d11iVec = null;

		if (VECTOR_COMPLEX_AVAILABLE) {
			d00rVec = DoubleVector.broadcast(SPECIES, d00r);
			d00iVec = DoubleVector.broadcast(SPECIES, d00i);
			d01rVec = DoubleVector.broadcast(SPECIES, d01r);
			d01iVec = DoubleVector.broadcast(SPECIES, d01i);
			d10rVec = DoubleVector.broadcast(SPECIES, d10r);
			d10iVec = DoubleVector.broadcast(SPECIES, d10i);
			d11rVec = DoubleVector.broadcast(SPECIES, d11r);
			d11iVec = DoubleVector.broadcast(SPECIES, d11i);
		}

		for (int baseHigh = 0; baseHigh < dimension; baseHigh += blockSize) {
			for (int baseMid = 0; baseMid < highStride; baseMid += betweenStep) {
				int baseOffset = baseHigh + baseMid;
				int offset = 0;
				int vectorLimit = VECTOR_COMPLEX_AVAILABLE ? lowStride - (lowStride % COMPLEX_PER_VECTOR) : 0;
				for (; offset < vectorLimit; offset += COMPLEX_PER_VECTOR) {
					int baseIndex = baseOffset + offset;
					int idx01 = baseIndex + offsetSecond;
					int idx10 = baseIndex + offsetFirst;
					int idx11 = baseIndex + offsetBoth;

					int off00 = toDoubleIndex(baseIndex);
					int off01 = toDoubleIndex(idx01);
					int off10 = toDoubleIndex(idx10);
					int off11 = toDoubleIndex(idx11);

					DoubleVector a00 = DoubleVector.fromArray(SPECIES, state, off00);
					DoubleVector a01 = DoubleVector.fromArray(SPECIES, state, off01);
					DoubleVector a10 = DoubleVector.fromArray(SPECIES, state, off10);
					DoubleVector a11 = DoubleVector.fromArray(SPECIES, state, off11);

					complexMul(a00, d00rVec, d00iVec).intoArray(state, off00);
					complexMul(a01, d01rVec, d01iVec).intoArray(state, off01);
					complexMul(a10, d10rVec, d10iVec).intoArray(state, off10);
					complexMul(a11, d11rVec, d11iVec).intoArray(state, off11);
				}
				for (; offset < lowStride; offset++) {
					int baseIndex = baseOffset + offset;
					int idx01 = baseIndex + offsetSecond;
					int idx10 = baseIndex + offsetFirst;
					int idx11 = baseIndex + offsetBoth;

					int off00 = toDoubleIndex(baseIndex);
					int off01 = toDoubleIndex(idx01);
					int off10 = toDoubleIndex(idx10);
					int off11 = toDoubleIndex(idx11);

					double a00r = state[off00];
					double a00i = state[off00 + 1];
					double a01r = state[off01];
					double a01i = state[off01 + 1];
					double a10r = state[off10];
					double a10i = state[off10 + 1];
					double a11r = state[off11];
					double a11i = state[off11 + 1];

					double new00r = d00r * a00r - d00i * a00i;
					double new00i = d00r * a00i + d00i * a00r;
					double new01r = d01r * a01r - d01i * a01i;
					double new01i = d01r * a01i + d01i * a01r;
					double new10r = d10r * a10r - d10i * a10i;
					double new10i = d10r * a10i + d10i * a10r;
					double new11r = d11r * a11r - d11i * a11i;
					double new11i = d11r * a11i + d11i * a11r;

					state[off00] = new00r;
					state[off00 + 1] = new00i;
					state[off01] = new01r;
					state[off01 + 1] = new01i;
					state[off10] = new10r;
					state[off10 + 1] = new10i;
					state[off11] = new11r;
					state[off11 + 1] = new11i;
				}
			}
		}
	}

	public static void applySwap(double[] state, int first, int second) {
		int low = Math.min(first, second);
		int high = Math.max(first, second);
		boolean firstIsLow = first == low;
		int lowMask = 1 << low;
		int highMask = 1 << high;
		int offsetFirst = firstIsLow ? lowMask : highMask;
		int offsetSecond = firstIsLow ? highMask : lowMask;
		int lowStride = lowMask;
		int highStride = 1 << high;
		int blockSize = highStride << 1;
		int betweenStep = lowMask << 1;
		int dimension = state.length >> 1;

		for (int baseHigh = 0; baseHigh < dimension; baseHigh += blockSize) {
			for (int baseMid = 0; baseMid < highStride; baseMid += betweenStep) {
				int baseOffset = baseHigh + baseMid;
				int offset = 0;
				int vectorLimit = VECTOR_COMPLEX_AVAILABLE ? lowStride - (lowStride % COMPLEX_PER_VECTOR) : 0;
				for (; offset < vectorLimit; offset += COMPLEX_PER_VECTOR) {
					int baseIndex = baseOffset + offset;
					int idxFirst = baseIndex + offsetFirst;
					int idxSecond = baseIndex + offsetSecond;

					int offFirst = toDoubleIndex(idxFirst);
					int offSecond = toDoubleIndex(idxSecond);

					DoubleVector firstVec = DoubleVector.fromArray(SPECIES, state, offFirst);
					DoubleVector secondVec = DoubleVector.fromArray(SPECIES, state, offSecond);

					secondVec.intoArray(state, offFirst);
					firstVec.intoArray(state, offSecond);
				}
				for (; offset < lowStride; offset++) {
					int baseIndex = baseOffset + offset;
					int idxFirst = baseIndex + offsetFirst;
					int idxSecond = baseIndex + offsetSecond;

					int offFirst = toDoubleIndex(idxFirst);
					int offSecond = toDoubleIndex(idxSecond);

					double firstR = state[offFirst];
					double firstI = state[offFirst + 1];
					double secondR = state[offSecond];
					double secondI = state[offSecond + 1];

					state[offFirst] = secondR;
					state[offFirst + 1] = secondI;
					state[offSecond] = firstR;
					state[offSecond + 1] = firstI;
				}
			}
		}
	}

	public static void applyMultiControlledSingleGate(double[] state, int target, int controlMask, double g00r,
			double g00i, double g01r, double g01i, double g10r, double g10i, double g11r, double g11i) {
		int stride = 1 << target;
		int period = stride << 1;
		int dimension = state.length >> 1;

		DoubleVector g00rVec = null;
		DoubleVector g00iVec = null;
		DoubleVector g01rVec = null;
		DoubleVector g01iVec = null;
		DoubleVector g10rVec = null;
		DoubleVector g10iVec = null;
		DoubleVector g11rVec = null;
		DoubleVector g11iVec = null;
		if (VECTOR_COMPLEX_AVAILABLE) {
			g00rVec = DoubleVector.broadcast(SPECIES, g00r);
			g00iVec = DoubleVector.broadcast(SPECIES, g00i);
			g01rVec = DoubleVector.broadcast(SPECIES, g01r);
			g01iVec = DoubleVector.broadcast(SPECIES, g01i);
			g10rVec = DoubleVector.broadcast(SPECIES, g10r);
			g10iVec = DoubleVector.broadcast(SPECIES, g10i);
			g11rVec = DoubleVector.broadcast(SPECIES, g11r);
			g11iVec = DoubleVector.broadcast(SPECIES, g11i);
		}

		for (int base = 0; base < dimension; base += period) {
			int offset = 0;
			int vectorLimit = VECTOR_COMPLEX_AVAILABLE ? stride - (stride % COMPLEX_PER_VECTOR) : 0;
			for (; offset < vectorLimit; offset += COMPLEX_PER_VECTOR) {
				int idx0 = base + offset;
				VectorMask<Double> laneMask = maskForControls(idx0, controlMask);
				if (!laneMask.anyTrue()) {
					continue;
				}
				int idx1 = idx0 + stride;
				int off0 = toDoubleIndex(idx0);
				int off1 = toDoubleIndex(idx1);

				DoubleVector a0 = DoubleVector.fromArray(SPECIES, state, off0);
				DoubleVector a1 = DoubleVector.fromArray(SPECIES, state, off1);

				DoubleVector new0 = complexMul(a0, g00rVec, g00iVec).add(complexMul(a1, g01rVec, g01iVec));
				DoubleVector new1 = complexMul(a0, g10rVec, g10iVec).add(complexMul(a1, g11rVec, g11iVec));

				new0.intoArray(state, off0, laneMask);
				new1.intoArray(state, off1, laneMask);
			}
			for (; offset < stride; offset++) {
				int idx0 = base + offset;
				if ((idx0 & controlMask) != controlMask) {
					continue;
				}
				int idx1 = idx0 + stride;
				int off0 = toDoubleIndex(idx0);
				int off1 = toDoubleIndex(idx1);

				double a0r = state[off0];
				double a0i = state[off0 + 1];
				double a1r = state[off1];
				double a1i = state[off1 + 1];

				double new0r = g00r * a0r - g00i * a0i + g01r * a1r - g01i * a1i;
				double new0i = g00r * a0i + g00i * a0r + g01r * a1i + g01i * a1r;
				double new1r = g10r * a0r - g10i * a0i + g11r * a1r - g11i * a1i;
				double new1i = g10r * a0i + g10i * a0r + g11r * a1i + g11i * a1r;

				state[off0] = new0r;
				state[off0 + 1] = new0i;
				state[off1] = new1r;
				state[off1 + 1] = new1i;
			}
		}
	}

	private static DoubleVector complexMul(DoubleVector value, DoubleVector realCoeff, DoubleVector imagCoeff) {
		DoubleVector realTerm = value.mul(realCoeff);
		DoubleVector imagTerm = value.rearrange(SWAP_SHUFFLE).mul(imagCoeff);
		return realTerm.add(imagTerm.mul(SIGN_VECTOR));
	}

	private static int toDoubleIndex(int complexIndex) {
		return complexIndex << 1;
	}

	private static VectorShuffle<Double> buildSwapShuffle() {
		int lanes = SPECIES.length();
		int[] indexes = new int[lanes];
		for (int lane = 0; lane < lanes; lane++) {
			indexes[lane] = lane ^ 1;
		}
		return VectorShuffle.fromArray(SPECIES, indexes, 0);
	}

	private static DoubleVector buildSignVector() {
		int lanes = SPECIES.length();
		double[] signs = new double[lanes];
		for (int lane = 0; lane < lanes; lane++) {
			signs[lane] = (lane & 1) == 0 ? -1.0 : 1.0;
		}
		return DoubleVector.fromArray(SPECIES, signs, 0);
	}

	private static VectorMask<Double> maskForControls(int baseComplex, int controlMask) {
		if (!VECTOR_COMPLEX_AVAILABLE) {
			return VectorMask.fromLong(SPECIES, 0L);
		}
		long bits = 0L;
		for (int lane = 0; lane < COMPLEX_PER_VECTOR; lane++) {
			int index = baseComplex + lane;
			if ((index & controlMask) == controlMask) {
				int doubleLane = lane << 1;
				bits |= (1L << doubleLane) | (1L << (doubleLane + 1));
			}
		}
		return VectorMask.fromLong(SPECIES, bits);
	}
}
