package com.omaarr90.statecraft.engines.tensornetwork;

import java.util.ArrayList;
import java.util.List;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;

/**
 * Complex SVD adapter backed by EJML's real-valued SVD.
 *
 * <p>Given an m x n complex matrix C = X + iY, we form the real block matrix:
 *
 * <pre>
 * [ X  -Y ]
 * [ Y   X ]
 * </pre>
 *
 * and run EJML's dense real SVD on that matrix. Singular values occur in pairs
 * for this representation; we consume one vector per pair to reconstruct a
 * complex factorization.
 */
final class EjmlComplexSvdAdapter implements ComplexSvdAdapter {

    @Override
    public SvdSplit split(int rows, int cols, double[] matrixData, int maxRank, double singularCutoff) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("rows and cols must be positive");
        }
        if (maxRank <= 0) {
            throw new IllegalArgumentException("maxRank must be positive");
        }
        if (!Double.isFinite(singularCutoff) || singularCutoff < 0.0) {
            throw new IllegalArgumentException("singularCutoff must be finite and non-negative");
        }
        int expectedLength = rows * cols * 2;
        if (matrixData.length != expectedLength) {
            throw new IllegalArgumentException(
                    "matrixData length mismatch: expected " + expectedLength + ", got " + matrixData.length);
        }

        DMatrixRMaj realified = toRealifiedMatrix(rows, cols, matrixData);
        SingularValueDecomposition_F64<DMatrixRMaj> svd =
                DecompositionFactory_DDRM.svd(realified.numRows, realified.numCols, false, true, true);
        if (!svd.decompose(realified)) {
            throw new IllegalStateException("EJML SVD decomposition failed");
        }

        double[] singularValues = svd.getSingularValues();
        DMatrixRMaj vReal = svd.getV(null, false);

        int complexModes = Math.min(rows, cols);
        int svCount = Math.min(svd.numberOfSingularValues(), singularValues.length);
        List<Mode> modes = recoverComplexModes(
                rows,
                cols,
                matrixData,
                singularValues,
                svCount,
                vReal,
                complexModes);
        if (modes.isEmpty()) {
            throw new IllegalStateException("failed to recover complex singular vectors from EJML realified SVD");
        }

        int rank = 0;
        int rankLimit = Math.min(maxRank, modes.size());
        for (int mode = 0; mode < rankLimit; mode++) {
            if (modes.get(mode).sigma > singularCutoff) {
                rank++;
            }
        }
        if (rank == 0) {
            rank = 1;
        }

        double keptNormSq = 0.0;
        for (int index = 0; index < rank; index++) {
            double value = modes.get(index).sigma;
            keptNormSq += value * value;
        }
        double totalNormSq = 0.0;
        for (Mode mode : modes) {
            totalNormSq += mode.sigma * mode.sigma;
        }
        double discardedWeight = Math.max(0.0, totalNormSq - keptNormSq);
        double renormalization = keptNormSq > 0.0 ? 1.0 / Math.sqrt(keptNormSq) : 1.0;

        double[] leftFactor = new double[rows * rank * 2];
        double[] rightFactor = new double[rank * cols * 2];
        for (int mode = 0; mode < rank; mode++) {
            Mode recovered = modes.get(mode);
            double sigma = recovered.sigma * renormalization;

            for (int row = 0; row < rows; row++) {
                int leftIndex = complexIndex(row, mode, rank);
                int source = row << 1;
                leftFactor[leftIndex] = recovered.leftVector[source];
                leftFactor[leftIndex + 1] = recovered.leftVector[source + 1];
            }

            for (int col = 0; col < cols; col++) {
                int source = col << 1;
                double vr = recovered.rightVector[source];
                double vi = recovered.rightVector[source + 1];
                int rightIndex = complexIndex(mode, col, cols);
                rightFactor[rightIndex] = sigma * vr;
                rightFactor[rightIndex + 1] = -sigma * vi;
            }
        }

        return new SvdSplit(rows, cols, rank, leftFactor, rightFactor, discardedWeight);
    }

    private static DMatrixRMaj toRealifiedMatrix(int rows, int cols, double[] matrixData) {
        DMatrixRMaj out = new DMatrixRMaj(rows << 1, cols << 1);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = complexIndex(row, col, cols);
                double real = matrixData[index];
                double imag = matrixData[index + 1];
                out.unsafe_set(row, col, real);
                out.unsafe_set(row, col + cols, -imag);
                out.unsafe_set(row + rows, col, imag);
                out.unsafe_set(row + rows, col + cols, real);
            }
        }
        return out;
    }

    private static List<Mode> recoverComplexModes(
            int rows,
            int cols,
            double[] matrixData,
            double[] singularValues,
            int svCount,
            DMatrixRMaj vReal,
            int complexModes) {
        List<Mode> modes = new ArrayList<>(complexModes);
        List<double[]> basis = new ArrayList<>(complexModes);

        int start = 0;
        while (start < svCount && modes.size() < complexModes) {
            double sigma = singularValues[start];
            if (!(sigma > 0.0)) {
                break;
            }
            int end = start + 1;
            while (end < svCount && almostEqual(singularValues[end], sigma)) {
                end++;
            }
            int groupSize = end - start;
            int groupTarget = Math.max(1, groupSize / 2);
            int groupAdded = 0;
            for (int column = start; column < end && modes.size() < complexModes && groupAdded < groupTarget; column++) {
                double[] candidate = extractComplexVector(vReal, cols, column);
                orthonormalize(candidate, basis);
                double norm = normalize(candidate);
                if (norm <= 1e-10) {
                    continue;
                }
                double[] left = multiply(matrixData, rows, cols, candidate);
                scale(left, 1.0 / sigma);
                double leftNorm = norm(left);
                if (leftNorm <= 1e-10) {
                    continue;
                }
                basis.add(candidate);
                modes.add(new Mode(sigma, candidate, left));
                groupAdded++;
            }
            start = end;
        }

        if (modes.size() < complexModes) {
            for (int column = 0; column < svCount && modes.size() < complexModes; column++) {
                double sigma = singularValues[column];
                if (!(sigma > 0.0)) {
                    break;
                }
                double[] candidate = extractComplexVector(vReal, cols, column);
                orthonormalize(candidate, basis);
                double norm = normalize(candidate);
                if (norm <= 1e-10) {
                    continue;
                }
                double[] left = multiply(matrixData, rows, cols, candidate);
                scale(left, 1.0 / sigma);
                double leftNorm = norm(left);
                if (leftNorm <= 1e-10) {
                    continue;
                }
                basis.add(candidate);
                modes.add(new Mode(sigma, candidate, left));
            }
        }

        return modes;
    }

    private static boolean almostEqual(double a, double b) {
        double tolerance = 1e-9 * Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
        return Math.abs(a - b) <= tolerance;
    }

    private static double[] extractComplexVector(DMatrixRMaj matrix, int complexLength, int column) {
        double[] out = new double[complexLength * 2];
        for (int row = 0; row < complexLength; row++) {
            int index = row << 1;
            out[index] = matrix.unsafe_get(row, column);
            out[index + 1] = matrix.unsafe_get(row + complexLength, column);
        }
        return out;
    }

    private static void orthonormalize(double[] candidate, List<double[]> basis) {
        for (double[] vector : basis) {
            double dotReal = 0.0;
            double dotImag = 0.0;
            for (int index = 0; index < candidate.length; index += 2) {
                double cr = candidate[index];
                double ci = candidate[index + 1];
                double vr = vector[index];
                double vi = vector[index + 1];
                dotReal += (vr * cr) + (vi * ci);
                dotImag += (vr * ci) - (vi * cr);
            }
            for (int index = 0; index < candidate.length; index += 2) {
                double vr = vector[index];
                double vi = vector[index + 1];
                candidate[index] -= (dotReal * vr) - (dotImag * vi);
                candidate[index + 1] -= (dotReal * vi) + (dotImag * vr);
            }
        }
    }

    private static double normalize(double[] vector) {
        double normSq = normSq(vector);
        if (normSq <= 0.0) {
            return 0.0;
        }
        double scale = 1.0 / Math.sqrt(normSq);
        for (int index = 0; index < vector.length; index++) {
            vector[index] *= scale;
        }
        return Math.sqrt(normSq);
    }

    private static double norm(double[] vector) {
        double normSq = normSq(vector);
        return normSq <= 0.0 ? 0.0 : Math.sqrt(normSq);
    }

    private static double normSq(double[] vector) {
        double normSq = 0.0;
        for (int index = 0; index < vector.length; index += 2) {
            double real = vector[index];
            double imag = vector[index + 1];
            normSq += (real * real) + (imag * imag);
        }
        return normSq;
    }

    private static double[] multiply(double[] matrixData, int rows, int cols, double[] vector) {
        double[] out = new double[rows * 2];
        for (int row = 0; row < rows; row++) {
            double sumReal = 0.0;
            double sumImag = 0.0;
            for (int col = 0; col < cols; col++) {
                int matrixIndex = complexIndex(row, col, cols);
                int vectorIndex = col << 1;
                double mr = matrixData[matrixIndex];
                double mi = matrixData[matrixIndex + 1];
                double vr = vector[vectorIndex];
                double vi = vector[vectorIndex + 1];
                sumReal += (mr * vr) - (mi * vi);
                sumImag += (mr * vi) + (mi * vr);
            }
            int outIndex = row << 1;
            out[outIndex] = sumReal;
            out[outIndex + 1] = sumImag;
        }
        return out;
    }

    private static void scale(double[] vector, double factor) {
        for (int index = 0; index < vector.length; index++) {
            vector[index] *= factor;
        }
    }

    private static int complexIndex(int row, int col, int cols) {
        return ((row * cols) + col) << 1;
    }

    private static final class Mode {
        private final double sigma;
        private final double[] rightVector;
        private final double[] leftVector;

        private Mode(double sigma, double[] rightVector, double[] leftVector) {
            this.sigma = sigma;
            this.rightVector = rightVector;
            this.leftVector = leftVector;
        }
    }
}
