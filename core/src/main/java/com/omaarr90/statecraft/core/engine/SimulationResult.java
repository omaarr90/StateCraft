package com.omaarr90.statecraft.core.engine;

import com.omaarr90.statecraft.quantum.StateVector;
import java.util.Objects;

/**
 * Result payload returned by a simulator engine.
 */
public record SimulationResult(StateVector finalState) {

    public SimulationResult {
        Objects.requireNonNull(finalState, "finalState");
    }
}
