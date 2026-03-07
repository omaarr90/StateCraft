package com.omaarr90.statecraft.engines.stabilizer;

import com.omaarr90.statecraft.quantum.StateVector;

final class StabilizerAmplitudeMirror {

    private static final double INV_SQRT_2 = 1.0 / Math.sqrt(2.0);

    private final int qubitCount;
    private final double[] data;

    private StabilizerAmplitudeMirror(int qubitCount, double[] data) {
        this.qubitCount = qubitCount;
        this.data = data;
    }

    static StabilizerAmplitudeMirror zeroState(int qubitCount) {
        int dimension = 1 << qubitCount;
        double[] data = new double[dimension << 1];
        data[0] = 1.0;
        return new StabilizerAmplitudeMirror(qubitCount, data);
    }

    static StabilizerAmplitudeMirror basisState(int qubitCount, int[] qubits) {
        int dimension = 1 << qubitCount;
        double[] data = new double[dimension << 1];
        int basisIndex = 0;
        for (int qubit : qubits) {
            basisIndex |= 1 << qubit;
        }
        data[basisIndex << 1] = 1.0;
        return new StabilizerAmplitudeMirror(qubitCount, data);
    }

    static StabilizerAmplitudeMirror fromStateVector(StateVector state) {
        return new StabilizerAmplitudeMirror(state.qubitCount(), state.copyData());
    }

    void applyHadamard(int target) {
        applySingle(target,
                INV_SQRT_2, 0.0,
                INV_SQRT_2, 0.0,
                INV_SQRT_2, 0.0,
                -INV_SQRT_2, 0.0);
    }

    void applyS(int target) {
        applySingle(target,
                1.0, 0.0,
                0.0, 0.0,
                0.0, 0.0,
                0.0, 1.0);
    }

    void applySdg(int target) {
        applySingle(target,
                1.0, 0.0,
                0.0, 0.0,
                0.0, 0.0,
                0.0, -1.0);
    }

    void applyX(int target) {
        applySingle(target,
                0.0, 0.0,
                1.0, 0.0,
                1.0, 0.0,
                0.0, 0.0);
    }

    void applyY(int target) {
        applySingle(target,
                0.0, 0.0,
                0.0, -1.0,
                0.0, 1.0,
                0.0, 0.0);
    }

    void applyZ(int target) {
        applySingle(target,
                1.0, 0.0,
                0.0, 0.0,
                0.0, 0.0,
                -1.0, 0.0);
    }

    void applyCnot(int control, int target) {
        int controlMask = 1 << control;
        int targetMask = 1 << target;
        int pairMask = controlMask | targetMask;
        int dimension = 1 << qubitCount;
        for (int basis = 0; basis < dimension; basis++) {
            if ((basis & pairMask) == controlMask) {
                swapAmplitudes(basis, basis | targetMask);
            }
        }
    }

    void applyCz(int control, int target) {
        int controlMask = 1 << control;
        int targetMask = 1 << target;
        int dimension = 1 << qubitCount;
        for (int basis = 0; basis < dimension; basis++) {
            if ((basis & controlMask) != 0 && (basis & targetMask) != 0) {
                int base = basis << 1;
                data[base] = -data[base];
                data[base + 1] = -data[base + 1];
            }
        }
    }

    void applySwap(int first, int second) {
        int firstMask = 1 << first;
        int secondMask = 1 << second;
        int dimension = 1 << qubitCount;
        for (int basis = 0; basis < dimension; basis++) {
            if ((basis & firstMask) == 0 && (basis & secondMask) == 0) {
                swapAmplitudes(basis | secondMask, basis | firstMask);
            }
        }
    }

    StateVector toStateVector() {
        return StateVector.fromArray(qubitCount, data);
    }

    private void applySingle(
            int target,
            double g00r,
            double g00i,
            double g01r,
            double g01i,
            double g10r,
            double g10i,
            double g11r,
            double g11i) {
        int stride = 1 << target;
        int period = stride << 1;
        int dimension = 1 << qubitCount;
        for (int base = 0; base < dimension; base += period) {
            for (int offset = 0; offset < stride; offset++) {
                int idx0 = base + offset;
                int idx1 = idx0 + stride;
                int off0 = idx0 << 1;
                int off1 = idx1 << 1;

                double a0r = data[off0];
                double a0i = data[off0 + 1];
                double a1r = data[off1];
                double a1i = data[off1 + 1];

                double new0r = (g00r * a0r) - (g00i * a0i) + (g01r * a1r) - (g01i * a1i);
                double new0i = (g00r * a0i) + (g00i * a0r) + (g01r * a1i) + (g01i * a1r);
                double new1r = (g10r * a0r) - (g10i * a0i) + (g11r * a1r) - (g11i * a1i);
                double new1i = (g10r * a0i) + (g10i * a0r) + (g11r * a1i) + (g11i * a1r);

                data[off0] = new0r;
                data[off0 + 1] = new0i;
                data[off1] = new1r;
                data[off1 + 1] = new1i;
            }
        }
    }

    private void swapAmplitudes(int firstBasis, int secondBasis) {
        int first = firstBasis << 1;
        int second = secondBasis << 1;
        double real = data[first];
        double imag = data[first + 1];
        data[first] = data[second];
        data[first + 1] = data[second + 1];
        data[second] = real;
        data[second + 1] = imag;
    }
}
