package com.omaarr90.statecraft.engines.tensornetwork;

import java.util.Objects;

final class MpsTensor {

    static final int PHYSICAL_DIM = 2;

    final int leftDim;
    final int rightDim;
    final double[] data;

    MpsTensor(int leftDim, int rightDim, double[] data) {
        if (leftDim <= 0) {
            throw new IllegalArgumentException("leftDim must be positive");
        }
        if (rightDim <= 0) {
            throw new IllegalArgumentException("rightDim must be positive");
        }
        this.leftDim = leftDim;
        this.rightDim = rightDim;
        this.data = Objects.requireNonNull(data, "data");
        int expectedLength = leftDim * PHYSICAL_DIM * rightDim * 2;
        if (data.length != expectedLength) {
            throw new IllegalArgumentException(
                    "tensor length mismatch: expected " + expectedLength + ", got " + data.length);
        }
    }

    static MpsTensor basisState(int bit) {
        if (bit != 0 && bit != 1) {
            throw new IllegalArgumentException("bit must be 0 or 1");
        }
        double[] data = new double[4];
        data[(bit * 2)] = 1.0;
        return new MpsTensor(1, 1, data);
    }

    int complexIndex(int left, int physical, int right) {
        return ((((left * PHYSICAL_DIM) + physical) * rightDim) + right) << 1;
    }

    double real(int left, int physical, int right) {
        return data[complexIndex(left, physical, right)];
    }

    double imag(int left, int physical, int right) {
        return data[complexIndex(left, physical, right) + 1];
    }

    void set(int left, int physical, int right, double real, double imag) {
        int index = complexIndex(left, physical, right);
        data[index] = real;
        data[index + 1] = imag;
    }
}
