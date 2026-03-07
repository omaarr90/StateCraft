package com.omaarr90.statecraft.engines.statevector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.MeasurementResult;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatevectorAlgorithmSuiteTest {

    private static final int SHOTS = 4_096;

    @Test
    void deutschJozsaConstantOracleYieldsAllZeroInputRegister() {
        int inputQubits = 3;
        int ancilla = inputQubits;

        QuantumCircuit circuit = new QuantumCircuit(inputQubits + 1)
                .append(new PauliX(), ancilla);

        for (int q = 0; q < inputQubits; q++) {
            circuit = circuit.append(new Hadamard(), q);
        }
        circuit = circuit.append(new Hadamard(), ancilla);

        // Constant f(x) = 0 oracle: no-op.

        for (int q = 0; q < inputQubits; q++) {
            circuit = circuit.append(new Hadamard(), q);
        }

        assertDeterministicOutcome(circuit, 0, 0, 1, 2);
    }

    @Test
    void deutschJozsaBalancedOracleYieldsNonZeroWitness() {
        int inputQubits = 3;
        int ancilla = inputQubits;

        QuantumCircuit circuit = new QuantumCircuit(inputQubits + 1)
                .append(new PauliX(), ancilla);

        for (int q = 0; q < inputQubits; q++) {
            circuit = circuit.append(new Hadamard(), q);
        }
        circuit = circuit.append(new Hadamard(), ancilla);

        // Balanced f(x) = x0 oracle.
        circuit = circuit.append(CnotGate.of(), 0, ancilla);

        for (int q = 0; q < inputQubits; q++) {
            circuit = circuit.append(new Hadamard(), q);
        }

        // Witness bitstring is 001 in q2 q1 q0 display => integer 1 in little-endian.
        assertDeterministicOutcome(circuit, 1, 0, 1, 2);
    }

    @Test
    void bernsteinVaziraniRecoversSecretBitstringExactly() {
        int inputQubits = 4;
        int ancilla = inputQubits;
        int secret = 0b1011; // q3 q2 q1 q0 = 1 0 1 1

        QuantumCircuit circuit = new QuantumCircuit(inputQubits + 1)
                .append(new PauliX(), ancilla);

        for (int q = 0; q < inputQubits; q++) {
            circuit = circuit.append(new Hadamard(), q);
        }
        circuit = circuit.append(new Hadamard(), ancilla);

        for (int q = 0; q < inputQubits; q++) {
            if (((secret >> q) & 1) == 1) {
                circuit = circuit.append(CnotGate.of(), q, ancilla);
            }
        }

        for (int q = 0; q < inputQubits; q++) {
            circuit = circuit.append(new Hadamard(), q);
        }

        assertDeterministicOutcome(circuit, secret, 0, 1, 2, 3);
    }

    @Test
    void phaseEstimationProducesExactBinaryPhaseForEigenstate() {
        // 1-bit QPE for U = Z on |1>, eigenphase phi = 1/2.
        // This should deterministically yield measurement outcome "1".
        int counting = 0;
        int target = 1;
        QuantumCircuit circuit = new QuantumCircuit(2)
                .append(new PauliX(), target)
                .append(new Hadamard(), counting)
                .appendControlledPhase(Math.PI, counting, target)
                .append(new Hadamard(), counting);

        assertDeterministicOutcome(circuit, 1, counting);
    }

    private static void assertDeterministicOutcome(QuantumCircuit circuit, int expected, int... measuredQubits) {
        MeasurementInstruction instruction = MeasurementInstruction.counts(SHOTS, measuredQubits).withSeed(11L);
        SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(instruction, false);

        SimulationResult result = new StatevectorEngine().simulate(request);
        MeasurementResult.Histogram histogram = (MeasurementResult.Histogram) result.measurement().orElseThrow();
        Map<Integer, Integer> counts = histogram.counts();

        assertTrue(result.finalState().isEmpty());
        assertEquals(SHOTS, histogram.shots());
        assertEquals(SHOTS, counts.getOrDefault(expected, 0), "Unexpected deterministic outcome: " + counts);
        assertEquals(1, counts.size(), "Expected a single deterministic measurement outcome: " + counts);
    }
}
