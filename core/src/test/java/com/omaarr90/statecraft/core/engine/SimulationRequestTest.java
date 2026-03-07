package com.omaarr90.statecraft.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.omaarr90.statecraft.core.noise.ErrorChannel;
import com.omaarr90.statecraft.core.noise.NoiseModel;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.StateVector;
import org.junit.jupiter.api.Test;

class SimulationRequestTest {

    @Test
    void zeroStateFactoryWrapsCircuit() {
        QuantumCircuit circuit = new QuantumCircuit(2);
        SimulationRequest request = SimulationRequest.zeroState(circuit);
        assertEquals(circuit, request.circuit());
        assertTrue(request.initialState().isEmpty());
        assertTrue(request.measurement().isEmpty());
        assertTrue(request.noiseModel().isEmpty());
        assertTrue(request.noiseSeed().isEmpty());
        assertTrue(request.returnFinalState());
    }

    @Test
    void explicitStateMatchesCircuitQubitCount() {
        QuantumCircuit circuit = new QuantumCircuit(1);
        StateVector state = StateVector.zero(1);
        SimulationRequest request = SimulationRequest.withInitialState(circuit, state);
        assertEquals(state, request.initialState().orElseThrow());
        assertTrue(request.measurement().isEmpty());
        assertTrue(request.noiseModel().isEmpty());
        assertTrue(request.noiseSeed().isEmpty());
        assertTrue(request.returnFinalState());
    }

    @Test
    void mismatchedQubitCountThrows() {
        QuantumCircuit circuit = new QuantumCircuit(1);
        StateVector state = StateVector.zero(2);
        assertThrows(IllegalArgumentException.class,
                () -> SimulationRequest.withInitialState(circuit, state));
    }

    @Test
    void withMeasurementCanDisableFinalState() {
        QuantumCircuit circuit = new QuantumCircuit(1);
        MeasurementInstruction instruction = MeasurementInstruction.countsAll(256);
        SimulationRequest base = SimulationRequest.zeroState(circuit);
        SimulationRequest request = base.withMeasurement(instruction, false);
        assertTrue(request.measurement().isPresent());
        assertFalse(request.returnFinalState());
    }

    @Test
    void droppingFinalStateWithoutMeasurementFails() {
        QuantumCircuit circuit = new QuantumCircuit(1);
        SimulationRequest request = SimulationRequest.zeroState(circuit);
        assertThrows(IllegalStateException.class, request::withoutFinalState);
    }

    @Test
    void measurementQubitOutOfRangeThrows() {
        QuantumCircuit circuit = new QuantumCircuit(2);
        MeasurementInstruction instruction = MeasurementInstruction.counts(32, 2);
        assertThrows(IllegalArgumentException.class,
                () -> SimulationRequest.zeroState(circuit).withMeasurement(instruction));
    }

    @Test
    void noiseModelAndSeedAreStored() {
        QuantumCircuit circuit = new QuantumCircuit(1);
        NoiseModel model = NoiseModel.builder()
                .afterAllGates(ErrorChannel.phaseFlip(0.2, 0))
                .build();
        SimulationRequest request = SimulationRequest.zeroState(circuit)
                .withNoiseModel(model)
                .withNoiseSeed(42L);

        assertEquals(model, request.noiseModel().orElseThrow());
        assertTrue(request.noiseSeed().isPresent());
        assertEquals(42L, request.noiseSeed().getAsLong());
    }

    @Test
    void basisStateQubitsAreStoredAndNormalized() {
        QuantumCircuit circuit = new QuantumCircuit(4);
        SimulationRequest request = SimulationRequest.zeroState(circuit).withBasisState(3, 1);

        assertTrue(request.initialState().isEmpty());
        assertArrayEquals(new int[] {1, 3}, request.basisStateQubits().orElseThrow());
    }

    @Test
    void initialStateAndBasisStateAreMutuallyExclusive() {
        QuantumCircuit circuit = new QuantumCircuit(2);
        StateVector state = StateVector.zero(2);

        assertThrows(IllegalArgumentException.class,
                () -> new SimulationRequest(
                        circuit,
                        java.util.Optional.of(state),
                        java.util.Optional.of(new int[] {1}),
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        java.util.OptionalLong.empty(),
                        true));
    }
}
