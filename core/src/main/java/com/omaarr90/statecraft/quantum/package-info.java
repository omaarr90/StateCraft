/**
 * Circuit construction primitives, built-in gates, and dense state snapshots.
 * <p>
 * Most applications start with
 * {@link com.omaarr90.statecraft.quantum.QuantumCircuit} and append gate
 * operations such as {@link com.omaarr90.statecraft.quantum.Hadamard},
 * {@link com.omaarr90.statecraft.quantum.PauliX}, and
 * {@link com.omaarr90.statecraft.quantum.CnotGate}. Circuits are immutable;
 * append methods return a new circuit instance.
 * <p>
 * {@link com.omaarr90.statecraft.quantum.StateVector} stores amplitudes as
 * interleaved real and imaginary doubles using little-endian qubit indexing.
 */
package com.omaarr90.statecraft.quantum;
