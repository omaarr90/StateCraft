package com.omaarr90.statecraft.quantum;

import com.omaarr90.statecraft.core.math.ComplexNumber;

public sealed interface SingleQubitGate permits PauliX, PauliY, PauliZ, Hadamard {

    String name();

    ComplexNumber element(int row, int col);

    default ComplexNumber[] applyTo(ComplexNumber alpha0, ComplexNumber alpha1) {
        ComplexNumber out0 = element(0, 0).times(alpha0).plus(element(0, 1).times(alpha1));
        ComplexNumber out1 = element(1, 0).times(alpha0).plus(element(1, 1).times(alpha1));
        return new ComplexNumber[] { out0, out1 };
    }
}
