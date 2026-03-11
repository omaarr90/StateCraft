package com.omaarr90.statecraft.quantum;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.Objects;

/**
 * Immutable snapshot of a quantum state vector using interleaved AoS doubles.
 */
public record StateVector(int qubitCount, double[] data) {

    private static final int MAX_DENSE_QUBITS = 29;

    public StateVector {
        qubitCount = validateQubitCount(qubitCount);
        Objects.requireNonNull(data, "data");
        int dimension = 1 << qubitCount;
        int expectedLength = dimension << 1;
        if (data.length != expectedLength) {
            throw new IllegalArgumentException(
                    "Expected " + expectedLength + " doubles for " + qubitCount + " qubits, got " + data.length);
        }
        data = data.clone();
    }

    public static StateVector zero(int qubitCount) {
        qubitCount = validateQubitCount(qubitCount);
        int dimension = 1 << qubitCount;
        double[] data = new double[dimension << 1];
        data[0] = 1.0; // |0...0> amplitude
        return new StateVector(qubitCount, data);
    }

    public static StateVector fromArray(int qubitCount, double[] data) {
        return new StateVector(qubitCount, data.clone());
    }

    public int dimension() {
        return 1 << qubitCount;
    }

    public ComplexNumber amplitude(int index) {
        validateIndex(index);
        int base = index << 1;
        return new ComplexNumber(data[base], data[base + 1]);
    }

    public double real(int index) {
        validateIndex(index);
        return data[index << 1];
    }

    public double imag(int index) {
        validateIndex(index);
        return data[(index << 1) + 1];
    }

    public double[] copyData() {
        return data.clone();
    }

    @Override
    public double[] data() {
        return data.clone();
    }

    private void validateIndex(int index) {
        if (index < 0 || index >= dimension()) {
            throw new IllegalArgumentException("index out of range: " + index);
        }
    }

    private static int validateQubitCount(int qubitCount) {
        if (qubitCount <= 0) {
            throw new IllegalArgumentException("qubitCount must be positive");
        }
        if (qubitCount > MAX_DENSE_QUBITS) {
            throw new IllegalArgumentException(
                    "dense statevector supports at most " + MAX_DENSE_QUBITS + " qubits, got " + qubitCount);
        }
        return qubitCount;
    }
}
