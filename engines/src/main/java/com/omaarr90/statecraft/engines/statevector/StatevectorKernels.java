package com.omaarr90.statecraft.engines.statevector;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;

final class StatevectorKernels {

    private static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;

    private StatevectorKernels() {
    }

    static void applySingleGate(double[] real, double[] imag, int target,
            double g00r, double g00i,
            double g01r, double g01i,
            double g10r, double g10i,
            double g11r, double g11i) {
        int dimension = real.length;
        int stride = 1 << target;
        int period = stride << 1;

        DoubleVector g00rVec = DoubleVector.broadcast(SPECIES, g00r);
        DoubleVector g00iVec = DoubleVector.broadcast(SPECIES, g00i);
        DoubleVector g01rVec = DoubleVector.broadcast(SPECIES, g01r);
        DoubleVector g01iVec = DoubleVector.broadcast(SPECIES, g01i);
        DoubleVector g10rVec = DoubleVector.broadcast(SPECIES, g10r);
        DoubleVector g10iVec = DoubleVector.broadcast(SPECIES, g10i);
        DoubleVector g11rVec = DoubleVector.broadcast(SPECIES, g11r);
        DoubleVector g11iVec = DoubleVector.broadcast(SPECIES, g11i);

        for (int base = 0; base < dimension; base += period) {
            int vectorLimit = SPECIES.loopBound(stride);
            int offset = 0;
            for (; offset < vectorLimit; offset += SPECIES.length()) {
                int idx0 = base + offset;
                int idx1 = idx0 + stride;

                DoubleVector a0r = DoubleVector.fromArray(SPECIES, real, idx0);
                DoubleVector a0i = DoubleVector.fromArray(SPECIES, imag, idx0);
                DoubleVector a1r = DoubleVector.fromArray(SPECIES, real, idx1);
                DoubleVector a1i = DoubleVector.fromArray(SPECIES, imag, idx1);

                DoubleVector new0r = g00rVec.mul(a0r).sub(g00iVec.mul(a0i))
                        .add(g01rVec.mul(a1r)).sub(g01iVec.mul(a1i));
                DoubleVector new0i = g00rVec.mul(a0i).add(g00iVec.mul(a0r))
                        .add(g01rVec.mul(a1i)).add(g01iVec.mul(a1r));

                DoubleVector new1r = g10rVec.mul(a0r).sub(g10iVec.mul(a0i))
                        .add(g11rVec.mul(a1r)).sub(g11iVec.mul(a1i));
                DoubleVector new1i = g10rVec.mul(a0i).add(g10iVec.mul(a0r))
                        .add(g11rVec.mul(a1i)).add(g11iVec.mul(a1r));

                new0r.intoArray(real, idx0);
                new0i.intoArray(imag, idx0);
                new1r.intoArray(real, idx1);
                new1i.intoArray(imag, idx1);
            }
            for (; offset < stride; offset++) {
                int idx0 = base + offset;
                int idx1 = idx0 + stride;
                double a0r = real[idx0];
                double a0i = imag[idx0];
                double a1r = real[idx1];
                double a1i = imag[idx1];

                double new0r = g00r * a0r - g00i * a0i + g01r * a1r - g01i * a1i;
                double new0i = g00r * a0i + g00i * a0r + g01r * a1i + g01i * a1r;
                double new1r = g10r * a0r - g10i * a0i + g11r * a1r - g11i * a1i;
                double new1i = g10r * a0i + g10i * a0r + g11r * a1i + g11i * a1r;

                real[idx0] = new0r;
                imag[idx0] = new0i;
                real[idx1] = new1r;
                imag[idx1] = new1i;
            }
        }
    }

    static void applyCnot(double[] real, double[] imag, int control, int target) {
        int controlMask = 1 << control;
        int targetMask = 1 << target;
        int dimension = real.length;
        for (int index = 0; index < dimension; index++) {
            if ((index & controlMask) == controlMask && (index & targetMask) == 0) {
                int flipped = index | targetMask;
                double tmpR = real[index];
                double tmpI = imag[index];
                real[index] = real[flipped];
                imag[index] = imag[flipped];
                real[flipped] = tmpR;
                imag[flipped] = tmpI;
            }
        }
    }
}
