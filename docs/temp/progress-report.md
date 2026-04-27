# Progress Report: StateCraft Noise Layer (Phase 4)

Name: Omar Alshammari
ID: 200819560
University: KFUPM
Course: COE 619

## Introduction (Phases 1-3 Summary)
Phase 1 established the project foundation: a multi-module Gradle layout, GraalVM native-image configuration, a CLI entry point, and initial tooling scaffolding. Phase 2 delivered the circuit ingest pipeline by adding a parser API plus JSON and OpenQASM 3 (subset) parsers, format detection, and end-to-end tests that run parsed circuits on the engine. Phase 3 completed the statevector MVP with SIMD-accelerated kernels, measurement sampling (counts and samples), and a small algorithm suite (e.g., Bell, GHZ, QFT) backed by unit and integration tests. These phases provided a stable engine and API surface to integrate noise in Phase 4.

## Phase 4 Objectives
- Integrate a configurable noise model into the statevector engine.
- Apply Kraus-operator sampling after each unitary gate.
- Support deterministic noise trajectories via seeded RNG.
- Validate behavior with targeted unit tests.

## Phase 4 Implementation Details
### Noise model and channels
- `NoiseModel` supports three attachment modes: gate-type specific, per-qubit, and global-after-every-gate. A builder produces immutable models; the legacy fluent constructor remains for backward compatibility.
- `NoiseModel.channelsAfter(Operation)` aggregates gate, qubit, and global channels based on the operation's affected qubits.
- `ErrorChannel` provides factory methods for depolarizing, amplitude damping, phase flip, phase damping, thermal relaxation, and composite channels.
- `KrausDecomposition` validates operator dimensions and exposes the channel matrices used for Monte Carlo sampling.
- Thermal relaxation now uses a CPTP Kraus decomposition derived from `T1`, `T2`, and gate time, with completeness checks covered by unit tests.

### SimulationRequest integration
- `SimulationRequest` now carries optional `NoiseModel` and optional `noiseSeed`, alongside the circuit, initial state, and measurement instruction.
- Request validation ensures qubit counts and measurement constraints are respected and that either a final state or measurement is requested.

### Statevector engine integration
- `StatevectorEngine` instantiates a `SplittableRandom` for noise sampling when a model is provided.
- After each unitary operation, the engine calls `applyNoiseAfterOperation` and applies each channel in the list returned by the noise model.
- `applyErrorChannel` handles composite channels recursively, validates target/decomposition sizes, and checks qubit ranges.
- Single-qubit Kraus application uses the existing single-qubit kernel; generic multi-qubit Kraus application uses a target-layout helper. Both paths renormalize the state after the sampled operator is applied.
- Measurements remain a terminal suffix; noise is not applied after measurements, and unitary gates after measurement are rejected.

### CLI integration
- `demo`, `run`, and `suite` share noise options for global depolarizing, amplitude damping, phase flip, phase damping, and thermal relaxation channels.
- `--noise-config` loads JSON configs with optional `noiseSeed` and global channel values; CLI flags and config values are merged, with `--noise-seed` taking precedence over config seeds.
- Seed-only noise inputs are rejected so users cannot accidentally request deterministic noise sampling without configured noise channels.

## Code Snippet (Engine Integration)
```java
NoiseModel noiseModel = request.noiseModel().orElse(null);
boolean applyNoise = noiseModel != null && noiseModel.hasNoise();
SplittableRandom noiseRng = applyNoise
        ? (request.noiseSeed().isPresent()
                ? new SplittableRandom(request.noiseSeed().getAsLong())
                : new SplittableRandom())
        : null;

for (QuantumCircuit.Operation operation : circuit.operations()) {
    if (operation instanceof QuantumCircuit.Operation.SingleGateOperation single) {
        applySingle(state, single);
        if (applyNoise) {
            applyNoiseAfterOperation(state, operation, noiseModel, noiseRng, qubitCount);
        }
    }
    // other unitary operations omitted for brevity
}
```

## Verification and Proof
The proof is encoded in tests that check deterministic sampling trajectories, correct channel distributions, and regressions where zero-probability branches must be skipped. The current engine computes branch probabilities from the live state via `||K_i|ψ⟩||²` before sampling:

```java
KrausOperator operator = sampleKrausOperator(state, channel, decomposition, targets, rng);
applySingleQubitKraus(state, target, operator);
```

Sample outputs from local runs show results with and without noise across two cases:

```text
Case 1: H on |0>
  Without noise: [0.7071067811865475, 0.0, 0.7071067811865475, 0.0]
  With phase-flip noise (p=1.0): [0.7071067811865475, 0.0, -0.7071067811865475, 0.0]
Case 2: X on |0>
  Without noise: [0.0, 0.0, 1.0, 0.0]
  With amplitude damping (gamma=0.4, seed=0): [1.0, 0.0, 0.0, 0.0]
```

## Challenges and Resolutions
- Deterministic sampling required consistent RNG behavior between engine and tests; resolved by injecting `noiseSeed` while deriving Kraus branch weights from the live state on every application.
- Kraus application changes state norm; a renormalization step was added after each operator to prevent drift.
- Generic multi-qubit Kraus application required target-layout handling; resolved by loading local target blocks, multiplying by the sampled Kraus matrix, and validating decomposition/target mismatches.
- Thermal-relaxation completeness drift was resolved by rebuilding the channel with a CPTP-consistent three-operator decomposition that no longer relies on static sampling weights.
- Noise application had to respect the measurement suffix constraint; noise is only applied to unitary operations.

## Current Limitations
- Built-in channel factories currently define single-qubit channels; correlated multi-qubit error models need first-class channel definitions before they are ergonomic to use.
- Idle-qubit noise is modeled through `NoiseModel.onQubits`, but there is no elapsed-time scheduler or per-gate duration metadata beyond thermal-relaxation gate time.
- CLI noise configuration targets global channels; gate-specific and per-qubit schedule design remains API-driven.
- One noise trajectory is sampled per simulation; no trajectory averaging is provided yet.

## Next Steps
1. Add trajectory averaging utilities to estimate noisy distributions more accurately.
2. Extend the CLI config schema for gate-specific and per-qubit schedules.
3. Add time-aware circuit scheduling and per-gate duration metadata for more physical idle-time noise.
4. Add first-class correlated multi-qubit channel definitions if the project needs those error models.
