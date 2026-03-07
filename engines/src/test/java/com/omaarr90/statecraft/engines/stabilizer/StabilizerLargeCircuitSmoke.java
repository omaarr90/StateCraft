package com.omaarr90.statecraft.engines.stabilizer;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.MeasurementResult;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.SGate;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/**
 * Manual smoke benchmark for large stabilizer runs.
 */
public final class StabilizerLargeCircuitSmoke {

    private static final int QUBITS = 1_000;
    private static final int DEPTH = 2_000;
    private static final int SHOTS = 64;

    private StabilizerLargeCircuitSmoke() {
    }

    public static void main(String[] args) {
        QuantumCircuit circuit = randomCircuit(new Random(0x51A817L), QUBITS, DEPTH);
        SimulationRequest request = SimulationRequest.zeroState(circuit)
                .withMeasurement(MeasurementInstruction.countsAll(SHOTS).withSeed(1234L), false);

        Instant start = Instant.now();
        MeasurementResult.BitstringHistogram histogram = (MeasurementResult.BitstringHistogram) new StabilizerEngine()
                .simulate(request)
                .measurement()
                .orElseThrow();
        Duration elapsed = Duration.between(start, Instant.now());

        System.out.println("stabilizer large smoke");
        System.out.println("qubits  : " + QUBITS);
        System.out.println("depth   : " + DEPTH);
        System.out.println("shots   : " + SHOTS);
        System.out.println("outcomes: " + histogram.counts().size());
        System.out.println("elapsed : " + elapsed.toMillis() + " ms");
    }

    private static QuantumCircuit randomCircuit(Random random, int qubits, int depth) {
        QuantumCircuit circuit = new QuantumCircuit(qubits);
        for (int step = 0; step < depth; step++) {
            if (random.nextBoolean()) {
                int target = random.nextInt(qubits);
                circuit = random.nextBoolean()
                        ? circuit.append(new Hadamard(), target)
                        : circuit.append(new SGate(), target);
            } else {
                int control = random.nextInt(qubits);
                int target = random.nextInt(qubits - 1);
                if (target >= control) {
                    target++;
                }
                circuit = circuit.append(CnotGate.of(), control, target);
            }
        }
        return circuit;
    }
}
