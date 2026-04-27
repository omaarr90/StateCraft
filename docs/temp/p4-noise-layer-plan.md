# Phase 4 Noise Layer Completion Notes

Phase 4 is implemented. The noise-aware statevector path applies scheduled `NoiseModel` channels after each unitary operation using state-dependent Kraus sampling, then returns either the final state, measurement output, or both through the existing engine API.

## Scope
- Completed: noise-aware backend, channel application (depolarizing/amplitude damping/phase flip/phase damping/T1/T2), gate/qubit/global scheduling, idle-qubit scheduling through `NoiseModel.onQubits`, deterministic noise seeds, CLI global noise flags/config loading, `SimulatorEngine` integration, tests, and P4 progress documentation.
- Remaining future work: trajectory averaging utilities, richer CLI schema for gate-specific/per-qubit schedules, and a time-aware scheduler beyond the current per-operation idle-qubit hook.

## Completed items
- [x] Reviewed `core/src/main/java/com/omaarr90/statecraft/core/noise/` and `engines/src/main/java/com/omaarr90/statecraft/engines/statevector/StatevectorEngine.java` to confirm integration strategy and sampling semantics.
- [x] Extended `core/src/main/java/com/omaarr90/statecraft/core/engine/SimulationRequest.java` to carry an optional `NoiseModel` and noise RNG seed.
- [x] Added statevector Kraus application helpers with branch-weight sampling, single- and multi-qubit matrix application, target validation, and renormalization.
- [x] Wired the engine loop to apply `NoiseModel.scheduledChannelsAfter(operation)` after unitary gates while keeping measurement as a terminal suffix.
- [x] Covered edge cases for empty models, invalid/duplicate qubit targets, deterministic noise seeds, zero-probability branches, seed-only configs, and measurement/noise interactions.
- [x] Added tests covering noisy evolution, scheduled noise paths, deterministic sampling, distribution sanity checks, thermal relaxation, CLI config parsing, and seeded measurement with noise.
- [x] Produced the Phase 4 progress report and updated the public docs with CLI usage, limitations, and verification notes.

## Resolved decisions
- Kraus operator selection uses state-dependent branch probabilities computed from the live state.
- Noise is integrated into the existing `statevector` engine so noiseless requests remain deterministic and unchanged.
- The API supports gate-specific, per-qubit, and global schedules; the CLI supports global noise channels through flags and JSON config files.
