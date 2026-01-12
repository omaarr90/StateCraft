package com.omaarr90.statecraft.core.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import org.junit.jupiter.api.Test;

class OpenQasmCircuitParserTest {

    private static final double EPS = 1e-12;

    @Test
    void bellCircuitParsesAndSimulates() {
        String qasm = """
                OPENQASM 3.0;
                qubit[2] q;

                h q[0];
                cx q[0], q[1];
                """;

        OpenQasmCircuitParser parser = new OpenQasmCircuitParser();
        QuantumCircuit circuit = parser.parse(qasm);

        QuantumCircuit expectedCircuit = new QuantumCircuit(2)
                .append(new Hadamard(), 0)
                .append(CnotGate.of(), 0, 1);

        assertStateMatches(expectedCircuit.apply(), circuit.apply());
    }

    @Test
    void measurementInMiddleIsRejected() {
        String qasm = """
                OPENQASM 3.0;
                qubit[1] q;
                bit[1] c;

                h q[0];
                measure q[0] -> c[0];
                x q[0];
                """;

        OpenQasmCircuitParser parser = new OpenQasmCircuitParser();
        CircuitParseException error = assertThrows(CircuitParseException.class, () -> parser.parse(qasm));
        assertTrue(error.getMessage().toLowerCase().contains("measurement"));
    }

    private static void assertStateMatches(ComplexNumber[] expected, ComplexNumber[] actual) {
        assertEquals(expected.length, actual.length);
        for (int index = 0; index < expected.length; index++) {
            ComplexNumber exp = expected[index] == null ? ComplexNumber.zero() : expected[index];
            ComplexNumber act = actual[index] == null ? ComplexNumber.zero() : actual[index];
            assertEquals(exp.real(), act.real(), EPS, "real mismatch at " + index);
            assertEquals(exp.imag(), act.imag(), EPS, "imag mismatch at " + index);
        }
    }
}
