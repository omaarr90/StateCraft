package com.omaarr90.statecraft.engines.statevector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.math.ComplexNumber;
import com.omaarr90.statecraft.core.parse.CircuitFormat;
import com.omaarr90.statecraft.core.parse.CircuitParsers;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CircuitParsingIntegrationTest {

    private static final double EPS = 1e-12;

    @Test
    void jsonParseThenSimulateMatchesCircuitApply(@TempDir Path tempDir) throws IOException {
        String json = """
                {
                  "qubits": 2,
                  "operations": [
                    { "gate": "h", "target": 0 },
                    { "gate": "cx", "control": 0, "target": 1 }
                  ]
                }
                """;

        Path input = tempDir.resolve("bell.json");
        Files.writeString(input, json);

        QuantumCircuit circuit = CircuitParsers.parse(input, CircuitFormat.AUTO);

        StatevectorEngine engine = new StatevectorEngine();
        SimulationResult result = engine.simulate(SimulationRequest.zeroState(circuit));

        assertStateMatches(circuit.apply(), result.finalState().orElseThrow());
    }

    private static void assertStateMatches(ComplexNumber[] expected, StateVector actual) {
        assertEquals(expected.length, actual.dimension());
        for (int index = 0; index < expected.length; index++) {
            ComplexNumber exp = expected[index] == null ? ComplexNumber.zero() : expected[index];
            assertEquals(exp.real(), actual.real(index), EPS, "real mismatch at " + index);
            assertEquals(exp.imag(), actual.imag(index), EPS, "imag mismatch at " + index);
        }
    }
}
