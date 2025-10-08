package com.omaarr90.statecraft.quantum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import org.junit.jupiter.api.Test;

class QuantumCircuitTest {

    private static final double EPS = 1e-9;

    @Test
    void xGateTurnsZeroIntoOne() {
        QuantumCircuit circuit = new QuantumCircuit(1);
        circuit = circuit.append(new PauliX(), 0);
        ComplexNumber[] result = circuit.apply();
        assertComplexEquals(ComplexNumber.zero(), result[0]);
        assertComplexEquals(ComplexNumber.one(), result[1]);
    }

    @Test
    void hadamardCreatesPlusState() {
        QuantumCircuit circuit = new QuantumCircuit(1).append(new Hadamard(), 0);
        ComplexNumber[] result = circuit.apply();
        double invSqrt2 = 1.0 / Math.sqrt(2.0);
        assertComplexEquals(new ComplexNumber(invSqrt2, 0.0), result[0]);
        assertComplexEquals(new ComplexNumber(invSqrt2, 0.0), result[1]);
    }

    @Test
    void twoQubitSequenceActsOnCorrectTargets() {
        QuantumCircuit circuit = new QuantumCircuit(2);
        circuit = circuit.append(new Hadamard(), 0);
        circuit = circuit.append(new PauliX(), 1);
        ComplexNumber[] result = circuit.apply();
        ComplexNumber zero = ComplexNumber.zero();
        ComplexNumber amp = new ComplexNumber(1.0 / Math.sqrt(2.0), 0.0);
        assertComplexEquals(zero, result[0]);
        assertComplexEquals(zero, result[1]);
        assertComplexEquals(amp, result[2]);
        assertComplexEquals(amp, result[3]);
    }

    @Test
    void zGateAddsPhaseToOneState() {
        QuantumCircuit circuit = new QuantumCircuit(1).append(new PauliZ(), 0);
        ComplexNumber[] initial = { ComplexNumber.zero(), ComplexNumber.one() };
        ComplexNumber[] result = circuit.apply(initial);
        assertComplexEquals(ComplexNumber.zero(), result[0]);
        assertComplexEquals(new ComplexNumber(-1.0, 0.0), result[1]);
    }

    private void assertComplexEquals(ComplexNumber expected, ComplexNumber actual) {
        assertEquals(expected.real(), actual.real(), EPS, "real parts differ");
        assertEquals(expected.imag(), actual.imag(), EPS, "imag parts differ");
    }
}
