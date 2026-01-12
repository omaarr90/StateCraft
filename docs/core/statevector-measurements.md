# Statevector Engine Measurement Semantics

This note supplements the existing statevector layout ADR with details about the
new measurement and shot-sampling capabilities exposed by the engine module.

## Request API

- `SimulationRequest` carries an optional `MeasurementInstruction`, optional
  noise configuration, and a `returnFinalState` flag. Callers can continue to
  omit the extra fields to retrieve only the final amplitudes, or attach an
  instruction to request sampling.
- `MeasurementInstruction` captures:
  - `shots`: strictly positive integer specifying how many samples to draw.
  - `mode`: either `COUNTS` (histogram of outcomes) or `SAMPLES` (raw outcomes).
  - `measuredQubits`: optional set of target qubits. When absent the engine
    defaults to any explicit `MeasureOperation`s at the tail of the circuit, or
    falls back to all qubits if the circuit does not declare measurements.
  - `seed`: optional RNG seed passed through to a `SplittableRandom` for
    reproducible sampling.
- Requests may drop the final state snapshot (`returnFinalState = false`) when
  measurements are present. The engine automatically rejects attempts to omit
  both amplitudes and measurement data.

## QuantumCircuit Support

- `QuantumCircuit.Operation` adds a `MeasureOperation`; circuits can now append
  measurement metadata via `measure(int... qubits)`. Measurements are validated
  to reference unique, in-range qubits.
- `QuantumCircuit.apply(...)` remains a pure-unitary evaluator and therefore
  throws when encountering a `MeasureOperation`. Circuits must place all
  measurement operations at the end; the statevector engine enforces the same
  constraint and rejects any subsequent unitary.

## StatevectorEngine Behaviour

- After the unitary portion of the circuit executes, the engine derives outcome
  probabilities from the final amplitudes and builds a cumulative distribution.
  Sampling uses binary search on the cumulative array (Walker alias is not yet
  required at current shot counts).
- Probability vectors are renormalised when rounding errors accumulate. A zero
  sum is treated as a fatal error because it indicates an invalid state vector.
- Measurement results surface through the sealed `MeasurementResult`
  hierarchy:
  - `Histogram` exposes counts per outcome (with keys representing basis states
    packed as integers whose bits align with the measured qubit order).
  - `Samples` exposes the raw per-shot outcomes in the same encoding.
- The engine continues to return amplitudes by default, but skips the
  `StateVector` allocation when `returnFinalState` is disabled.

## CLI Updates

- `statecraft demo` gained `--shots`, `--seed`, and `--samples` switches. When
  `--shots` is provided the CLI prints a histogram (or raw samples in
  `--samples` mode) alongside the existing amplitude listing.
- Validation prevents inconsistent combinations, e.g. `--samples` or `--seed`
  without `--shots`.

## Limitations and Follow-up Work

- Mid-circuit measurement collapse is not yet modelled; measurement operations
  must form a contiguous suffix.
- Histogram aggregation uses standard maps; alias tables or SIMD-accelerated
  samplers can be added if workloads demand higher shot counts.
