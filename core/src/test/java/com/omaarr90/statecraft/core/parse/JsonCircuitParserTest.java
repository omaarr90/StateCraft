package com.omaarr90.statecraft.core.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.PauliY;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import org.junit.jupiter.api.Test;

class JsonCircuitParserTest {

    private static final double EPS = 1e-12;

    @Test
    void bellCircuitParsesAndSimulates() {
        String json = """
                {
                  "qubits": 2,
                  "operations": [
                    { "gate": "h", "target": 0 },
                    { "gate": "cx", "control": 0, "target": 1 }
                  ]
                }
                """;

        JsonCircuitParser parser = new JsonCircuitParser();
        QuantumCircuit circuit = parser.parse(json);

        QuantumCircuit expectedCircuit = new QuantumCircuit(2)
                .append(new Hadamard(), 0)
                .append(CnotGate.of(), 0, 1);

        assertStateMatches(expectedCircuit.apply(), circuit.apply());
    }

    @Test
    void invalidGateNameIsRejected() {
        String json = """
                {
                  "qubits": 1,
                  "operations": [
                    { "gate": "foo", "target": 0 }
                  ]
                }
                """;

        JsonCircuitParser parser = new JsonCircuitParser();
        CircuitParseException error = assertThrows(CircuitParseException.class, () -> parser.parse(json));
        assertTrue(error.getMessage().contains("Unknown gate"));
    }

    @Test
    void controlledAndMultiControlGatesParseAndSimulate() {
        String json = """
                {
                  "qubits": 3,
                  "operations": [
                    { "gate": "x", "target": 0 },
                    { "gate": "x", "target": 1 },
                    { "gate": "ccx", "controls": [0, 1], "target": 2 },
                    { "gate": "mcy", "controls": [2], "target": 0 },
                    { "gate": "cz", "control": 2, "target": 1 }
                  ]
                }
                """;

        JsonCircuitParser parser = new JsonCircuitParser();
        QuantumCircuit circuit = parser.parse(json);

        QuantumCircuit expectedCircuit = new QuantumCircuit(3)
                .append(new PauliX(), 0)
                .append(new PauliX(), 1)
                .appendToffoli(0, 1, 2)
                .appendMultiControl(new PauliY(), 0, 2)
                .appendControlledZ(2, 1);

        assertStateMatches(expectedCircuit.apply(), circuit.apply());
    }

    @Test
    void measureWithoutQubitsDefaultsToAllQubits() {
        String json = """
                {
                  "qubits": 3,
                  "operations": [
                    { "gate": "h", "target": 0 },
                    { "gate": "barrier" },
                    { "gate": "measure" }
                  ]
                }
                """;

        JsonCircuitParser parser = new JsonCircuitParser();
        QuantumCircuit circuit = parser.parse(json);
        QuantumCircuit.Operation.MeasureOperation measure = (QuantumCircuit.Operation.MeasureOperation)
                circuit.operations().get(circuit.operations().size() - 1);

        assertArrayEquals(new int[] {0, 1, 2}, measure.qubits());
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
