/**
 * Noise model and quantum error channel APIs shared by simulator engines.
 * <p>
 * {@link com.omaarr90.statecraft.core.noise.NoiseModel} schedules
 * {@link com.omaarr90.statecraft.core.noise.ErrorChannel} instances after
 * specific gate types, after all gates, or on idle qubits. Built-in channels
 * include depolarizing, amplitude damping, phase flip, phase damping, thermal
 * relaxation, and channel composition.
 */
package com.omaarr90.statecraft.core.noise;
