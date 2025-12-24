package com.omaarr90.statecraft.core.noise;

import com.omaarr90.statecraft.quantum.QuantumCircuit.Operation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable noise model for attaching error channels to quantum circuit
 * operations.
 * <p>
 * A NoiseModel specifies which error channels should be applied after specific
 * gate types, on specific qubits, or after all operations. During simulation,
 * the engine queries the model to determine what noise to apply after each
 * gate.
 * <p>
 * Example usage with Builder pattern:
 * 
 * <pre>{@code
 * NoiseModel model = NoiseModel.builder()
 *         .afterGate(PauliX.class, ErrorChannel.depolarizing(0.01, 0))
 *         .afterGate(CnotGate.class, ErrorChannel.depolarizing(0.05, 0, 1))
 *         .afterAllGates(ErrorChannel.thermalRelaxation(50e-6, 30e-6, 100e-9, 0))
 *         .build();
 * }</pre>
 * <p>
 * For backward compatibility, the legacy constructor-based fluent API is still
 * supported:
 * 
 * <pre>{@code
 * NoiseModel model = new NoiseModel()
 *         .afterGate(PauliX.class, ErrorChannel.depolarizing(0.01, 0))
 *         .afterAllGates(ErrorChannel.thermalRelaxation(50e-6, 30e-6, 100e-9, 0));
 * }</pre>
 */
public final class NoiseModel {

    // Noise after specific gate types (class -> list of channels)
    private final Map<Class<? extends Operation>, List<ErrorChannel>> gateNoise;

    // Noise on specific qubits (qubit -> list of channels)
    private final Map<Integer, List<ErrorChannel>> qubitNoise;

    // Noise after every gate operation
    private final List<ErrorChannel> globalNoise;

    // Private constructor for Builder
    private NoiseModel(Builder builder) {
        // Create defensive copies of the builder's data structures
        this.gateNoise = new HashMap<>();
        builder.gateNoise.forEach((key, value) -> this.gateNoise.put(key, new ArrayList<>(value)));

        this.qubitNoise = new HashMap<>();
        builder.qubitNoise.forEach((key, value) -> this.qubitNoise.put(key, new ArrayList<>(value)));

        this.globalNoise = new ArrayList<>(builder.globalNoise);
    }

    /**
     * Legacy constructor for backward compatibility.
     * Creates an empty noise model that can be configured using fluent methods.
     * <p>
     * Note: This constructor creates a mutable NoiseModel for legacy compatibility.
     * For new code, prefer using {@link #builder()} to create immutable models.
     */
    public NoiseModel() {
        this.gateNoise = new HashMap<>();
        this.qubitNoise = new HashMap<>();
        this.globalNoise = new ArrayList<>();
    }

    /**
     * Creates a new Builder for constructing immutable NoiseModel instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Legacy fluent method: Adds noise that applies after a specific gate type.
     * <p>
     * Note: This method mutates the NoiseModel and is provided for backward
     * compatibility.
     * For new code, prefer using {@link Builder#afterGate(Class, ErrorChannel)}.
     *
     * @param gateType gate operation class
     * @param channel  error channel to apply
     * @return this model for chaining
     */
    public NoiseModel afterGate(Class<? extends Operation> gateType, ErrorChannel channel) {
        Objects.requireNonNull(gateType, "gateType");
        Objects.requireNonNull(channel, "channel");
        gateNoise.computeIfAbsent(gateType, k -> new ArrayList<>()).add(channel);
        return this;
    }

    /**
     * Legacy fluent method: Adds noise that applies on specific qubits.
     * <p>
     * This is useful for modeling decoherence that occurs even when no gates are
     * applied.
     * Note: Current implementation applies this noise whenever the qubit is
     * involved in
     * an operation, not during true idle times (which would require circuit
     * scheduling).
     * <p>
     * Note: This method mutates the NoiseModel and is provided for backward
     * compatibility.
     * For new code, prefer using {@link Builder#onQubits(ErrorChannel, int...)}.
     *
     * @param channel error channel to apply
     * @param qubits  target qubits
     * @return this model for chaining
     */
    public NoiseModel onQubits(ErrorChannel channel, int... qubits) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(qubits, "qubits");
        if (qubits.length == 0) {
            throw new IllegalArgumentException("must specify at least one qubit");
        }
        for (int qubit : qubits) {
            qubitNoise.computeIfAbsent(qubit, k -> new ArrayList<>()).add(channel);
        }
        return this;
    }

    /**
     * Legacy fluent method: Adds noise that applies after every gate operation.
     * <p>
     * Note: This method mutates the NoiseModel and is provided for backward
     * compatibility.
     * For new code, prefer using {@link Builder#afterAllGates(ErrorChannel)}.
     *
     * @param channel error channel to apply
     * @return this model for chaining
     */
    public NoiseModel afterAllGates(ErrorChannel channel) {
        Objects.requireNonNull(channel, "channel");
        globalNoise.add(channel);
        return this;
    }

    /**
     * Returns all error channels that should be applied after the given operation.
     * <p>
     * This includes:
     * <ul>
     * <li>Channels registered for this specific gate type</li>
     * <li>Channels registered for qubits involved in this operation</li>
     * <li>Global channels that apply after all gates</li>
     * </ul>
     *
     * @param operation the operation that just executed
     * @return list of error channels to apply (may be empty)
     */
    public List<ErrorChannel> channelsAfter(Operation operation) {
        Objects.requireNonNull(operation, "operation");
        List<ErrorChannel> channels = new ArrayList<>();

        // Add gate-specific noise
        List<ErrorChannel> gateChannels = gateNoise.get(operation.getClass());
        if (gateChannels != null) {
            channels.addAll(gateChannels);
        }

        // Add qubit-specific noise for affected qubits
        Set<Integer> affectedQubits = extractAffectedQubits(operation);
        for (int qubit : affectedQubits) {
            List<ErrorChannel> qChannels = qubitNoise.get(qubit);
            if (qChannels != null) {
                channels.addAll(qChannels);
            }
        }

        // Add global noise
        channels.addAll(globalNoise);

        return channels;
    }

    /**
     * Returns whether this model has any noise configured.
     */
    public boolean hasNoise() {
        return !gateNoise.isEmpty() || !qubitNoise.isEmpty() || !globalNoise.isEmpty();
    }

    private Set<Integer> extractAffectedQubits(Operation operation) {
        Set<Integer> qubits = new HashSet<>();
        // Use the operation's validateTargets method to infer target qubits
        // This is a bit of a hack, but works since all operations store their targets
        if (operation instanceof Operation.SingleGateOperation sgo) {
            qubits.add(sgo.qubit());
        } else if (operation instanceof Operation.CnotOperation cnot) {
            qubits.add(cnot.controlQubit());
            qubits.add(cnot.targetQubit());
        } else if (operation instanceof Operation.TwoQubitGateOperation twoq) {
            qubits.add(twoq.firstQubit());
            qubits.add(twoq.secondQubit());
        } else if (operation instanceof Operation.TwoQubitDiagonalOperation diag) {
            qubits.add(diag.firstQubit());
            qubits.add(diag.secondQubit());
        } else if (operation instanceof Operation.SwapOperation swap) {
            qubits.add(swap.firstQubit());
            qubits.add(swap.secondQubit());
        } else if (operation instanceof Operation.MultiControlOperation multi) {
            qubits.add(multi.targetQubit());
            for (int ctrl : multi.controlQubits()) {
                qubits.add(ctrl);
            }
        }
        return qubits;
    }

    /**
     * Builder for constructing immutable NoiseModel instances.
     * <p>
     * Provides a fluent API for configuring noise channels with validation
     * and defensive copying to ensure immutability of the final model.
     */
    public static final class Builder {
        private final Map<Class<? extends Operation>, List<ErrorChannel>> gateNoise = new HashMap<>();
        private final Map<Integer, List<ErrorChannel>> qubitNoise = new HashMap<>();
        private final List<ErrorChannel> globalNoise = new ArrayList<>();

        /**
         * Private constructor - use {@link NoiseModel#builder()} to create instances.
         */
        private Builder() {
        }

        /**
         * Adds noise that applies after a specific gate type.
         *
         * @param gateType gate operation class
         * @param channel  error channel to apply
         * @return this builder for chaining
         * @throws NullPointerException if gateType or channel is null
         */
        public Builder afterGate(Class<? extends Operation> gateType, ErrorChannel channel) {
            Objects.requireNonNull(gateType, "gateType");
            Objects.requireNonNull(channel, "channel");
            gateNoise.computeIfAbsent(gateType, k -> new ArrayList<>()).add(channel);
            return this;
        }

        /**
         * Adds noise that applies on specific qubits.
         * <p>
         * This is useful for modeling decoherence that occurs even when no gates are
         * applied.
         * Note: Current implementation applies this noise whenever the qubit is
         * involved in
         * an operation, not during true idle times (which would require circuit
         * scheduling).
         *
         * @param channel error channel to apply
         * @param qubits  target qubits (must be non-empty)
         * @return this builder for chaining
         * @throws NullPointerException     if channel or qubits is null
         * @throws IllegalArgumentException if qubits array is empty
         */
        public Builder onQubits(ErrorChannel channel, int... qubits) {
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(qubits, "qubits");
            if (qubits.length == 0) {
                throw new IllegalArgumentException("must specify at least one qubit");
            }
            for (int qubit : qubits) {
                qubitNoise.computeIfAbsent(qubit, k -> new ArrayList<>()).add(channel);
            }
            return this;
        }

        /**
         * Adds noise that applies after every gate operation.
         *
         * @param channel error channel to apply
         * @return this builder for chaining
         * @throws NullPointerException if channel is null
         */
        public Builder afterAllGates(ErrorChannel channel) {
            Objects.requireNonNull(channel, "channel");
            globalNoise.add(channel);
            return this;
        }

        /**
         * Builds an immutable NoiseModel with the configured noise channels.
         * <p>
         * The returned model is completely independent from this builder;
         * further modifications to the builder will not affect the returned model.
         *
         * @return a new immutable NoiseModel instance
         */
        public NoiseModel build() {
            return new NoiseModel(this);
        }
    }

    @Override
    public String toString() {
        return "NoiseModel[gateTypes=" + gateNoise.keySet().size() + ", qubits="
                + qubitNoise.keySet().size() + ", global=" + globalNoise.size() + "]";
    }
}
