# Plan

Implement a noise-aware statevector simulation path that applies the existing NoiseModel channels after each gate using Kraus sampling, then validate with tests and document the P4 progress. The approach is to extend the current engine/request API to carry noise configuration and apply per-gate/qubit/global channels in the execution loop.

## Scope
- In: noise-aware backend, channel application (depolarizing/amplitude damping/phase flip/T1/T2), gate/qubit/global granularity, SimulatorEngine integration, tests, P4 progress report.
- Out: multi-qubit Kraus support beyond single-qubit channels, full circuit scheduling/idle-time noise, CLI-driven noise configuration (unless required).

## Action items
[ ] Review `core/src/main/java/com/omaarr90/statecraft/core/noise/` and `engines/src/main/java/com/omaarr90/statecraft/engines/statevector/StatevectorEngine.java` to confirm integration strategy and sampling semantics.
[ ] Extend `core/src/main/java/com/omaarr90/statecraft/core/engine/SimulationRequest.java` to carry an optional `NoiseModel` (and RNG seed for noise sampling if needed), and document behavior in the engine API.
[ ] Add a statevector noise application helper (single-qubit Kraus operator multiply + renormalization) and guard unsupported channel sizes explicitly.
[ ] Wire the engine loop to apply `NoiseModel.channelsAfter(operation)` after each unitary gate; ensure measurement remains a suffix and noise is not applied after measurement operations.
[ ] Handle edge cases: empty/no-op noise models, invalid qubit indices, multi-qubit channels, and deterministic sampling with seeds for test reproducibility.
[ ] Add tests covering noisy evolution (e.g., amplitude damping on |1>, phase flip on |+>, depolarizing sanity) and integration tests that compare noisy results against expected distributions.
[ ] Produce the P4 progress report deliverable (update `docs/README.md` or add a dedicated `docs/deliverables/progress-report-2.md`) with scope, limitations, and how to run noisy simulations.

## Open questions
- Should Kraus operator selection use state-dependent probabilities or the fixed `KrausOperator.probability` values in the current noise model design?
- Do we integrate noise into the existing `statevector` engine or introduce a distinct engine id (e.g., `statevector-noisy`) to keep deterministic runs separate?
- Should noise configuration be API-only for P4, or do we also add CLI flags to load/compose a `NoiseModel`?
