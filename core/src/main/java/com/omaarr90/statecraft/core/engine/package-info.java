/**
 * Engine-neutral simulation request, result, discovery, and measurement APIs.
 * <p>
 * Use {@link com.omaarr90.statecraft.core.engine.SimulationRequest} to describe
 * the circuit, optional initial state, measurement instruction, noise model,
 * and final-state preference. Implementations of
 * {@link com.omaarr90.statecraft.core.engine.SimulatorEngine} execute the
 * request and return a
 * {@link com.omaarr90.statecraft.core.engine.SimulationResult}.
 * <p>
 * {@link com.omaarr90.statecraft.core.engine.SimulatorEngines} discovers engine
 * implementations through Java {@link java.util.ServiceLoader}.
 */
package com.omaarr90.statecraft.core.engine;
