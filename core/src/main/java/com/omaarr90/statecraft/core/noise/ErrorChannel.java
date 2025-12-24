package com.omaarr90.statecraft.core.noise;

import com.omaarr90.statecraft.core.math.ComplexNumber;

/**
 * A quantum error channel modeled using Kraus operators.
 * <p>
 * Error channels represent realistic noise processes that affect quantum
 * circuits, including depolarizing errors, amplitude damping, phase errors,
 * and thermal relaxation (T₁/T₂ decoherence).
 * <p>
 * Implementations provide a Kraus decomposition suitable for Monte Carlo
 * simulation with pure state vectors.
 */
public sealed interface ErrorChannel
        permits DepolarizingChannel,
                AmplitudeDampingChannel,
                PhaseFlipChannel,
                PhaseDampingChannel,
                ThermalRelaxationChannel,
                CompositeChannel {

    /**
     * Returns the Kraus decomposition for this error channel.
     */
    KrausDecomposition krausDecomposition();

    /**
     * Returns the qubits affected by this channel (0-indexed).
     */
    int[] affectedQubits();

    /**
     * Creates a depolarizing channel with the given error probability.
     * <p>
     * The depolarizing channel applies X, Y, or Z with probability p/3 each,
     * effectively randomizing the qubit state. With probability (1-p), the
     * qubit is left unchanged.
     *
     * @param probability error probability in [0,1]
     * @param qubits      target qubits (currently only single-qubit supported)
     * @return depolarizing error channel
     */
    static ErrorChannel depolarizing(double probability, int... qubits) {
        return new DepolarizingChannel(probability, qubits);
    }

    /**
     * Creates an amplitude damping channel modeling energy relaxation.
     * <p>
     * Models the |1⟩ → |0⟩ decay process with damping parameter γ.
     * Represents energy dissipation to the environment.
     *
     * @param gamma  damping parameter in [0,1]
     * @param qubits target qubits (currently only single-qubit supported)
     * @return amplitude damping channel
     */
    static ErrorChannel amplitudeDamping(double gamma, int... qubits) {
        return new AmplitudeDampingChannel(gamma, qubits);
    }

    /**
     * Creates a phase flip channel.
     * <p>
     * With probability p, applies a Z gate (phase flip). Otherwise leaves
     * the qubit unchanged. This models random phase errors without affecting
     * population.
     *
     * @param probability flip probability in [0,1]
     * @param qubits      target qubits (currently only single-qubit supported)
     * @return phase flip channel
     */
    static ErrorChannel phaseFlip(double probability, int... qubits) {
        return new PhaseFlipChannel(probability, qubits);
    }

    /**
     * Creates a phase damping channel modeling pure dephasing.
     * <p>
     * Models loss of quantum coherence without energy relaxation.
     * The parameter λ controls the strength of dephasing.
     *
     * @param lambda dephasing parameter in [0,1]
     * @param qubits target qubits (currently only single-qubit supported)
     * @return phase damping channel
     */
    static ErrorChannel phaseDamping(double lambda, int... qubits) {
        return new PhaseDampingChannel(lambda, qubits);
    }

    /**
     * Creates a thermal relaxation channel combining T₁ and T₂ decoherence.
     * <p>
     * Models realistic qubit decoherence including both energy relaxation
     * (T₁) and dephasing (T₂). The gate time determines how much decoherence
     * accumulates during the operation.
     *
     * @param t1       energy relaxation time in seconds (must be positive)
     * @param t2       dephasing time in seconds (must satisfy 0 < t2 <= 2*t1)
     * @param gateTime gate duration in seconds
     * @param qubits   target qubits (currently only single-qubit supported)
     * @return thermal relaxation channel
     */
    static ErrorChannel thermalRelaxation(double t1, double t2, double gateTime, int... qubits) {
        return new ThermalRelaxationChannel(t1, t2, gateTime, qubits);
    }

    /**
     * Composes multiple error channels sequentially.
     * <p>
     * The channels are applied in the order provided. This allows building
     * complex noise models from simpler components.
     *
     * @param channels channels to compose (must be non-empty)
     * @return composite error channel
     */
    static ErrorChannel compose(ErrorChannel... channels) {
        return new CompositeChannel(channels);
    }

    /**
     * Helper to create a single-qubit Kraus operator.
     */
    static KrausOperator singleQubitOperator(double probability, ComplexNumber... elements) {
        if (elements.length != 4) {
            throw new IllegalArgumentException("single-qubit operator requires exactly 4 elements");
        }
        return new KrausOperator(elements, probability);
    }
}
