package com.omaarr90.statecraft.quantum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import org.junit.jupiter.api.Test;

class SingleQubitGateTest {

    private static final double EPS = 1e-9;

    @Test
    void pauliXSwapsBasisStates() {
        SingleQubitGate gate = new PauliX();
        assertComplexEquals(ComplexNumber.zero(), gate.element(0, 0));
        assertComplexEquals(ComplexNumber.one(), gate.element(0, 1));
        assertComplexEquals(ComplexNumber.one(), gate.element(1, 0));
        assertComplexEquals(ComplexNumber.zero(), gate.element(1, 1));
    }

    @Test
    void pauliYHasImaginaryEntries() {
        SingleQubitGate gate = new PauliY();
        assertComplexEquals(ComplexNumber.zero(), gate.element(0, 0));
        assertComplexEquals(new ComplexNumber(0.0, -1.0), gate.element(0, 1));
        assertComplexEquals(new ComplexNumber(0.0, 1.0), gate.element(1, 0));
        assertComplexEquals(ComplexNumber.zero(), gate.element(1, 1));
    }

    @Test
    void pauliZFlipsPhase() {
        SingleQubitGate gate = new PauliZ();
        assertComplexEquals(ComplexNumber.one(), gate.element(0, 0));
        assertComplexEquals(ComplexNumber.zero(), gate.element(0, 1));
        assertComplexEquals(ComplexNumber.zero(), gate.element(1, 0));
        assertComplexEquals(new ComplexNumber(-1.0, 0.0), gate.element(1, 1));
    }

    @Test
    void hadamardEntriesHaveExpectedMagnitude() {
        SingleQubitGate gate = new Hadamard();
        double invSqrt2 = 1.0 / Math.sqrt(2.0);
        assertComplexEquals(new ComplexNumber(invSqrt2, 0.0), gate.element(0, 0));
        assertComplexEquals(new ComplexNumber(invSqrt2, 0.0), gate.element(0, 1));
        assertComplexEquals(new ComplexNumber(invSqrt2, 0.0), gate.element(1, 0));
        assertComplexEquals(new ComplexNumber(-invSqrt2, 0.0), gate.element(1, 1));
    }

    @Test
    void sGateAddsPositiveImaginaryPhase() {
        SingleQubitGate gate = new SGate();
        assertComplexEquals(ComplexNumber.one(), gate.element(0, 0));
        assertComplexEquals(ComplexNumber.zero(), gate.element(0, 1));
        assertComplexEquals(ComplexNumber.zero(), gate.element(1, 0));
        assertComplexEquals(new ComplexNumber(0.0, 1.0), gate.element(1, 1));
    }

    @Test
    void sdgGateAddsNegativeImaginaryPhase() {
        SingleQubitGate gate = new SdgGate();
        assertComplexEquals(ComplexNumber.one(), gate.element(0, 0));
        assertComplexEquals(ComplexNumber.zero(), gate.element(0, 1));
        assertComplexEquals(ComplexNumber.zero(), gate.element(1, 0));
        assertComplexEquals(new ComplexNumber(0.0, -1.0), gate.element(1, 1));
    }

    private void assertComplexEquals(ComplexNumber expected, ComplexNumber actual) {
        assertEquals(expected.real(), actual.real(), EPS, "real parts differ");
        assertEquals(expected.imag(), actual.imag(), EPS, "imag parts differ");
    }
}
