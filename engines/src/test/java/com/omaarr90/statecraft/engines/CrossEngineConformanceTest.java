package com.omaarr90.statecraft.engines;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.MeasurementResult;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulationResult;
import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.engines.stabilizer.StabilizerEngine;
import com.omaarr90.statecraft.engines.statevector.StatevectorEngine;
import com.omaarr90.statecraft.engines.tensornetwork.TensorNetworkEngine;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class CrossEngineConformanceTest {

    private static final double STATE_EPS = 1e-12;
    private static final int SHOTS = 8_192;
    private static final double DISTRIBUTION_TOL = 0.03;

    @Test
    void allTargetEnginesAreDiscoverable() {
        List<String> ids = ServiceLoader.load(SimulatorEngine.class).stream()
                .map(ServiceLoader.Provider::get)
                .map(SimulatorEngine::id)
                .toList();

        assertTrue(ids.contains(StatevectorEngine.ID));
        assertTrue(ids.contains(StabilizerEngine.ID));
        assertTrue(ids.contains(TensorNetworkEngine.ID));
    }

    @Test
    void finalStatesConformAcrossEngines() {
        List<QuantumCircuit> circuits = List.of(
                bellCircuit(),
                cliffordConformanceCircuit());

        SimulatorEngine reference = loadEngine(StatevectorEngine.ID);
        SimulatorEngine stabilizer = loadEngine(StabilizerEngine.ID);
        SimulatorEngine tensor = loadEngine(TensorNetworkEngine.ID);

        for (QuantumCircuit circuit : circuits) {
            SimulationRequest request = SimulationRequest.zeroState(circuit);

            StateVector expected = reference.simulate(request).finalState().orElseThrow();
            StateVector stabilizerState = stabilizer.simulate(request).finalState().orElseThrow();
            StateVector tensorState = tensor.simulate(request).finalState().orElseThrow();

            assertStateClose(expected, stabilizerState, "stabilizer");
            assertStateClose(expected, tensorState, "tensornetwork");
        }
    }

    @Test
    void shotHistogramsConformAcrossEnginesWithinTolerance() {
        QuantumCircuit circuit = cliffordConformanceCircuit();
        MeasurementInstruction instruction = MeasurementInstruction.countsAll(SHOTS).withSeed(0xC0FFEEL);
        SimulationRequest request = SimulationRequest.zeroState(circuit).withMeasurement(instruction, false);

        SimulatorEngine reference = loadEngine(StatevectorEngine.ID);
        SimulatorEngine stabilizer = loadEngine(StabilizerEngine.ID);
        SimulatorEngine tensor = loadEngine(TensorNetworkEngine.ID);

        MeasurementResult.Histogram expected =
                (MeasurementResult.Histogram) reference.simulate(request).measurement().orElseThrow();
        MeasurementResult.Histogram stabilizerHistogram =
                (MeasurementResult.Histogram) stabilizer.simulate(request).measurement().orElseThrow();
        MeasurementResult.Histogram tensorHistogram =
                (MeasurementResult.Histogram) tensor.simulate(request).measurement().orElseThrow();

        assertHistogramClose(expected, stabilizerHistogram, "stabilizer");
        assertHistogramClose(expected, tensorHistogram, "tensornetwork");
    }

    private static QuantumCircuit bellCircuit() {
        return new QuantumCircuit(2)
                .append(new Hadamard(), 0)
                .append(CnotGate.of(), 0, 1);
    }

    private static QuantumCircuit cliffordConformanceCircuit() {
        return new QuantumCircuit(4)
                .append(new Hadamard(), 0)
                .append(CnotGate.of(), 0, 1)
                .append(new PauliX(), 2)
                .appendSwap(1, 2)
                .appendControlledPhase(Math.PI, 2, 3)
                .append(CnotGate.of(), 3, 0)
                .append(new Hadamard(), 3);
    }

    private static SimulatorEngine loadEngine(String id) {
        SimulatorEngine engine = ServiceLoader.load(SimulatorEngine.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElse(null);
        assertNotNull(engine, "engine not found: " + id);
        return engine;
    }

    private static void assertStateClose(StateVector expected, StateVector actual, String engineId) {
        assertEquals(expected.dimension(), actual.dimension(), engineId + " dimension mismatch");
        for (int index = 0; index < expected.dimension(); index++) {
            assertEquals(expected.real(index), actual.real(index), STATE_EPS,
                    engineId + " real mismatch at basis " + index);
            assertEquals(expected.imag(index), actual.imag(index), STATE_EPS,
                    engineId + " imag mismatch at basis " + index);
        }
    }

    private static void assertHistogramClose(
            MeasurementResult.Histogram expected,
            MeasurementResult.Histogram actual,
            String engineId) {
        assertArrayEquals(expected.measuredQubits(), actual.measuredQubits(), engineId + " measured qubits mismatch");
        assertEquals(expected.shots(), actual.shots(), engineId + " shot count mismatch");

        int measuredBits = expected.measuredQubits().length;
        int outcomes = 1 << measuredBits;
        Map<Integer, Integer> expectedCounts = expected.counts();
        Map<Integer, Integer> actualCounts = actual.counts();
        double shots = expected.shots();

        for (int outcome = 0; outcome < outcomes; outcome++) {
            double pExpected = expectedCounts.getOrDefault(outcome, 0) / shots;
            double pActual = actualCounts.getOrDefault(outcome, 0) / shots;
            assertEquals(pExpected, pActual, DISTRIBUTION_TOL,
                    engineId + " distribution mismatch at outcome " + outcome);
        }
    }
}
