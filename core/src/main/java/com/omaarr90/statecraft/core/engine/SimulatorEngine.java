package com.omaarr90.statecraft.core.engine;

public interface SimulatorEngine {
    /// A short, stable identifier (e.g., "statevector").
    String id();

    /// Run the engine against the given request and return the resulting state.
    SimulationResult simulate(SimulationRequest request);
}
