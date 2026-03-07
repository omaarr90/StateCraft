# TensorNetwork Engine (MPS v1)

This document describes the production tensor-network backend implemented in
`engines/.../tensornetwork`.

## Summary

- Backend: Matrix Product State (MPS) simulation with SVD-based compression.
- Target workload: shallow circuits up to `50` qubits and depth `<= 40`.
- Optimization target: shots/measurement workflows on larger qubit counts.

## Defaults

- Max bond dimension: `256`
- Singular-value cutoff: `1e-10`
- Dense initial-state ingestion limit: `20` qubits
- Final-state materialization limit: `20` qubits

When truncation occurs, the kept singular values are renormalized so
measurement sampling remains well-defined.

## Supported Operations (v1)

- Single-qubit gates (`SingleGateOperation`)
- `CNOT`
- Two-qubit diagonal gates (`TwoQubitDiagonalOperation`, e.g. controlled phase)
- `SWAP`
- Terminal measurement suffixes

Non-adjacent two-qubit operations are executed through adjacent SWAP routing
plus logical-to-physical qubit tracking.

## Explicitly Unsupported (v1)

- Noisy simulation (`NoiseModel`)
- Arbitrary two-qubit unitary matrices (`TwoQubitGateOperation`)
- Multi-control gates (`MultiControlOperation`)
- Mid-circuit measurement/collapse semantics
- Large dense final-state output above the configured final-state limit

## Measurement Semantics

- Uses seeded `SplittableRandom` when a measurement seed is provided.
- Preserves existing engine API result types:
  - `Histogram` / `Samples` when measured width `<= 31`
  - `BitstringHistogram` / `BitstringSamples` when measured width `> 31`
