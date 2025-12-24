package com.omaarr90.statecraft.core.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.QuantumCircuit;
import com.omaarr90.statecraft.quantum.QuantumCircuit.Operation;
import com.omaarr90.statecraft.quantum.PauliX;
import com.omaarr90.statecraft.quantum.PauliY;
import com.omaarr90.statecraft.quantum.PauliZ;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for NoiseModel, focusing on the Builder pattern implementation.
 */
class NoiseModelTest {

    @Test
    void builderCreatesEmptyModel() {
        NoiseModel model = NoiseModel.builder().build();
        assertNotNull(model);
        assertFalse(model.hasNoise());
    }

    @Test
    void builderAddsGateNoise() {
        NoiseModel model = NoiseModel.builder()
                .afterGate(Operation.SingleGateOperation.class, ErrorChannel.depolarizing(0.01, 0))
                .build();

        assertTrue(model.hasNoise());

        Operation op = new QuantumCircuit.Operation.SingleGateOperation(new PauliX(), 0);
        List<ErrorChannel> channels = model.channelsAfter(op);
        assertEquals(1, channels.size());
    }

    @Test
    void builderAddsQubitNoise() {
        NoiseModel model = NoiseModel.builder()
                .onQubits(ErrorChannel.phaseDamping(0.05, 1), 1, 2, 3)
                .build();

        assertTrue(model.hasNoise());

        Operation op = new QuantumCircuit.Operation.SingleGateOperation(new PauliY(), 2);
        List<ErrorChannel> channels = model.channelsAfter(op);
        assertEquals(1, channels.size());
    }

    @Test
    void builderAddsGlobalNoise() {
        NoiseModel model = NoiseModel.builder()
                .afterAllGates(ErrorChannel.phaseFlip(0.02, 0))
                .build();

        assertTrue(model.hasNoise());

        Operation op = new QuantumCircuit.Operation.SingleGateOperation(new PauliZ(), 0);
        List<ErrorChannel> channels = model.channelsAfter(op);
        assertEquals(1, channels.size());
    }

    @Test
    void builderCombinesMultipleNoiseSources() {
        NoiseModel model = NoiseModel.builder()
                .afterGate(Operation.SingleGateOperation.class, ErrorChannel.depolarizing(0.01, 0))
                .onQubits(ErrorChannel.phaseDamping(0.03, 0), 0)
                .afterAllGates(ErrorChannel.amplitudeDamping(0.02, 0))
                .build();

        assertTrue(model.hasNoise());

        Operation op = new QuantumCircuit.Operation.SingleGateOperation(new PauliX(), 0);
        List<ErrorChannel> channels = model.channelsAfter(op);
        // Should have gate-specific + qubit-specific + global = 3 channels
        assertEquals(3, channels.size());
    }

    @Test
    void builderRejectsNullGateType() {
        NoiseModel.Builder builder = NoiseModel.builder();
        assertThrows(
                NullPointerException.class,
                () -> builder.afterGate(null, ErrorChannel.depolarizing(0.01, 0)));
    }

    @Test
    void builderRejectsNullChannel() {
        NoiseModel.Builder builder = NoiseModel.builder();
        assertThrows(NullPointerException.class, () -> builder.afterGate(Operation.SingleGateOperation.class, null));
    }

    @Test
    void builderRejectsEmptyQubits() {
        NoiseModel.Builder builder = NoiseModel.builder();
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.onQubits(ErrorChannel.phaseFlip(0.01, 0)));
    }

    @Test
    void builderIsIndependentAfterBuild() {
        NoiseModel.Builder builder = NoiseModel.builder()
                .afterGate(Operation.SingleGateOperation.class, ErrorChannel.depolarizing(0.01, 0));

        NoiseModel model1 = builder.build();

        // Add more noise after building
        builder.afterAllGates(ErrorChannel.phaseFlip(0.02, 0));
        NoiseModel model2 = builder.build();

        // First model should not be affected by later builder modifications
        Operation op = new QuantumCircuit.Operation.SingleGateOperation(new PauliX(), 0);
        assertEquals(1, model1.channelsAfter(op).size());
        assertEquals(2, model2.channelsAfter(op).size());
    }

    @Test
    void legacyConstructorStillWorksFluently() {
        NoiseModel model = new NoiseModel()
                .afterGate(Operation.SingleGateOperation.class, ErrorChannel.depolarizing(0.01, 0))
                .afterAllGates(ErrorChannel.phaseFlip(0.02, 0));

        assertTrue(model.hasNoise());

        Operation op = new QuantumCircuit.Operation.SingleGateOperation(new PauliX(), 0);
        List<ErrorChannel> channels = model.channelsAfter(op);
        assertEquals(2, channels.size());
    }

    @Test
    void channelsAfterReturnsEmptyForUnaffectedOperation() {
        NoiseModel model = NoiseModel.builder()
                .afterGate(Operation.SingleGateOperation.class, ErrorChannel.depolarizing(0.01, 0))
                .build();

        // A different operation type should not trigger single-gate noise
        Operation op = new QuantumCircuit.Operation.CnotOperation(CnotGate.of(), 0, 1);
        List<ErrorChannel> channels = model.channelsAfter(op);
        assertTrue(channels.isEmpty());
    }

    @Test
    void channelsAfterHandlesMultipleGateTypes() {
        NoiseModel model = NoiseModel.builder()
                .afterGate(Operation.SingleGateOperation.class, ErrorChannel.depolarizing(0.01, 0))
                .afterGate(Operation.CnotOperation.class, ErrorChannel.phaseFlip(0.02, 0))
                .build();

        Operation opSingle = new QuantumCircuit.Operation.SingleGateOperation(new PauliX(), 0);
        assertEquals(1, model.channelsAfter(opSingle).size());

        Operation opCnot = new QuantumCircuit.Operation.CnotOperation(CnotGate.of(), 0, 1);
        assertEquals(1, model.channelsAfter(opCnot).size());
    }

    @Test
    void toStringShowsNoiseCount() {
        NoiseModel model = NoiseModel.builder()
                .afterGate(Operation.SingleGateOperation.class, ErrorChannel.depolarizing(0.01, 0))
                .onQubits(ErrorChannel.phaseDamping(0.03, 0), 0, 1)
                .afterAllGates(ErrorChannel.phaseFlip(0.02, 0))
                .build();

        String str = model.toString();
        assertTrue(str.contains("gateTypes=1"));
        assertTrue(str.contains("qubits=2"));
        assertTrue(str.contains("global=1"));
    }
}
