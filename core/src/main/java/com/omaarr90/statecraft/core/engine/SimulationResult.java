package com.omaarr90.statecraft.core.engine;

import com.omaarr90.statecraft.quantum.StateVector;
import java.util.Objects;
import java.util.Optional;

/**
 * Result payload returned by a simulator engine.
 */
public record SimulationResult(Optional<StateVector> finalState, Optional<MeasurementResult> measurement) {

    public SimulationResult {
        finalState = finalState == null ? Optional.empty() : finalState;
        finalState.ifPresent(state -> Objects.requireNonNull(state, "finalState"));
        measurement = measurement == null ? Optional.empty() : measurement;
        measurement.ifPresent(result -> Objects.requireNonNull(result, "measurement"));
        if (finalState.isEmpty() && measurement.isEmpty()) {
            throw new IllegalArgumentException(
                    "SimulationResult must contain a state vector and/or measurement outcomes");
        }
    }

    public static SimulationResult forState(StateVector state) {
        Objects.requireNonNull(state, "state");
        return new SimulationResult(Optional.of(state), Optional.empty());
    }

    public static SimulationResult forMeasurement(MeasurementResult result) {
        Objects.requireNonNull(result, "result");
        return new SimulationResult(Optional.empty(), Optional.of(result));
    }

    public static SimulationResult forStateAndMeasurement(StateVector state, MeasurementResult result) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(result, "result");
        return new SimulationResult(Optional.of(state), Optional.of(result));
    }
}
