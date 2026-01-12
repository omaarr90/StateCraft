# StateCraft Project Overview

## Project summary
StateCraft is a Java-based quantum circuit simulator that targets high
performance and portability. The core library models circuits and noise in
plain Java, while the engine layer plugs in different simulation backends
(statevector today, stabilizer and tensor-network engines planned). The
application module exposes a CLI and GraalVM native-image packaging so users
can run simulations without a JVM at runtime.

The repository already contains a working statevector engine with SIMD
acceleration (JDK Vector API), measurement sampling, and a small algorithm
suite. A noise model with Kraus-operator primitives is implemented in the core
library, and the statevector engine now applies those channels after each gate
using Kraus sampling.

## Goals and scope (from the proposal)
- Correct, scalable simulation of circuits up to roughly 30 qubits.
- Noise modeling with depolarizing, amplitude damping, phase flip, phase
  damping, and T1/T2 thermal relaxation channels.
- Alternative engines for Clifford and shallow, larger circuits.
- SIMD and parallel execution where it improves throughput.
- A documented Java API plus a CLI packaged as a native binary.
- A small catalog of reference algorithms (Bell, GHZ, Deutsch-Jozsa,
  Bernstein-Vazirani, QFT, and phase estimation).

## Repository structure
- `core/`: math primitives, quantum circuit model, noise model, engine API.
- `engines/`: simulation backends (statevector engine currently implemented).
- `app/`: CLI wrapper with GraalVM native-image build configuration.
- `docs/`: progress reports, ADRs, and planning deliverables.
- `gradle/`, `build.gradle.kts`, `settings.gradle.kts`: Gradle build system.

## Core library features

### Math primitives
- `ComplexNumber`: immutable complex numbers with arithmetic helpers and cached
  zero/one instances.
- `ComplexArrays`: BLAS-like operations over interleaved complex buffers in
  `[re0, im0, re1, im1, ...]` layout, including:
  - scaled norms (`norm2`, `norm2Sq`) with overflow-safe scaling
  - complex scaling (`scal`) and axpy (`axpy`) for in-place fused updates
  - element-wise complex multiplication (`mul`) with aliasing support
  - strict validation of bounds, nulls, and alignment

### Quantum circuit model
- `SingleQubitGate` sealed interface with concrete Pauli X/Y/Z and Hadamard
  records.
- `CnotGate` value type for controlled-NOT.
- `QuantumCircuit` record that stores an ordered list of operations with
  validation for qubit ranges and duplicates. Supported operations:
  - `SingleGateOperation` for any `SingleQubitGate`
  - `CnotOperation`
  - `TwoQubitGateOperation` for 4x4 unitaries
  - `TwoQubitDiagonalOperation` for diagonal 2-qubit gates
  - `SwapOperation`
  - `MultiControlOperation` (multi-control single-qubit gate)
  - `MeasureOperation` (measurement metadata)
- Convenience builders such as `append`, `appendTwoQubitUnitary`,
  `appendControlledPhase`, `appendSwap`, `appendMultiControl`, and `measure`.
- `QuantumCircuit.apply(...)` is a pure-unitary evaluator; it throws if
  measurement operations are present.

### Circuit parsing
- `core.parse` defines the `CircuitParser` API plus `CircuitParseException`
  carrying optional line/column metadata.
- JSON schema supports `qubits` and an `operations` list with `h`, `x`, `y`,
  `z`, `cx`, `swap`, `cp`, and `measure` (measurement must be a suffix).
- OpenQASM 3 subset parser supports `OPENQASM 3.0;`, `qubit[n] q;`,
  optional `bit[n] c;`, and the same gate set with simple `pi` divisions.
- Format detection resolves an explicit format, then file extension, then an
  `OPENQASM` content prefix.

### State vector representation
- `StateVector` record stores a snapshot in interleaved AoS `double[]` form,
  with helper factories for the zero state and custom arrays.
- Index validation and defensive copying are enforced to keep states immutable.

### Noise modeling primitives
- `ErrorChannel` sealed interface with factory methods for:
  - depolarizing
  - amplitude damping
  - phase flip
  - phase damping
  - thermal relaxation (T1/T2 + gate time)
  - composite channels (sequential application)
- `KrausOperator` and `KrausDecomposition` enforce valid dimensions and
  probability normalization, and provide a sampling helper for Monte Carlo
  simulation.
- `NoiseModel` supports builder-based configuration of:
  - gate-type specific noise
  - per-qubit noise
  - global noise after every gate
  The builder creates immutable models while the legacy fluent constructor
  remains for backward compatibility.
- Current limitation: all built-in channels target a single qubit only.

### Engine API
- `SimulatorEngine` defines `id()` and `simulate(...)`.
- `SimulationRequest` wraps a circuit, optional initial state, optional
  measurement instruction, optional noise model + seed, and a flag to return the
  final state.
- `SimulationResult` returns a final state, measurement results, or both.
- `MeasurementInstruction` supports counts or samples, optional measured qubits,
  and optional RNG seeds.
- `MeasurementResult` is a sealed hierarchy for histograms and raw samples.

## Engine implementations

### Statevector engine
- `StatevectorEngine` implements `SimulatorEngine` using SIMD kernels built on
  the JDK Vector API.
- Supported operations:
  - single-qubit gates
  - CNOT
  - arbitrary 2-qubit unitaries
  - 2-qubit diagonal operations (including controlled phase)
  - SWAP
  - multi-control single-qubit gates
  - measurement as a terminal suffix
- Measurement sampling:
  - optional per-qubit measurement selection
  - counts or sample modes
  - deterministic results via seed
  - probability normalization with a cumulative distribution sampler
- Noise sampling:
  - optional `NoiseModel` applied after each unitary gate
  - single-qubit Kraus operators only (composite channels apply sequentially)
  - measurement operations remain a terminal suffix
- Measurements must appear after all unitary operations in a circuit; the
  engine rejects unitary gates that follow measurement operations.
- Registered through `ServiceLoader` in
  `engines/src/main/resources/META-INF/services/...`.

### Statevector kernels
- `StatevectorKernels` provides SIMD-aware routines for the supported gate
  operations using the interleaved AoS layout.
- Scalar fallbacks cover tail elements when vector widths do not align.
- Microbenchmark `StatevectorKernelMicrobenchmark` tracks AoS throughput.

## CLI application
- `statecraft engines`: lists available engines discovered via ServiceLoader.
- `statecraft demo`: runs a Bell-state circuit and optionally samples shots.
  - flags: `--shots`, `--seed`, `--samples`.
- `statecraft run`: loads a circuit file (`--input`) in JSON or QASM
  (`--format qasm|json|auto`) and runs it on the selected engine.
- `statecraft suite`: runs a small algorithm suite and prints operations,
  amplitudes, and shot histograms for each circuit.
- Output formatting includes amplitude prettification and bitstring conversion
  (MSB on the left, LSB is q0).

## Build and tooling
- Multi-module Gradle build with Java 25 toolchain.
- JUnit 5 for tests; Spotless for formatting.
- JDK Vector API is enabled with `--enable-preview --add-modules
  jdk.incubator.vector` in engines and CLI.
- GraalVM native-image plugin configured in `app/` to build a `statecraft`
  binary.

## Documentation assets
- `docs/README.md`: current progress report and architecture summary.
- `docs/core/statevector-layout.md`: ADR for bit order and AoS layout.
- `docs/core/statevector-measurements.md`: measurement API and semantics.
- `docs/deliverables/project-proposal.md`: proposal, milestones, and phases.

## Current limitations and gaps
- OpenQASM 3 support is limited to a small subset and JSON schema coverage is
  intentionally minimal; expand grammar coverage and gate support over time.
- Noise simulation is limited to single-qubit Kraus channels; idle-time noise
  and multi-qubit channels are not modeled.
- Alternative engines (stabilizer, tensor network) are not implemented.
- CI is disabled while waiting for GraalVM 25 support in GitHub Actions.
- Mid-circuit measurement collapse is not modeled; measurements must be a
  contiguous suffix.

## Remaining work to complete Phases 1 to 4

### Phase 1: Repo setup, Gradle skeleton, CI, GraalVM toolchain
Current status: repo and Gradle structure are in place; GraalVM native-image
build exists; CI workflow exists but is disabled.

Remaining work:
- Re-enable GitHub Actions CI on push/PR once GraalVM 25 is available.
- Update the native-image job to target the correct module
  (use `:app:nativeCompile` instead of the legacy `:cli:nativeCompile`).
- Verify CI flags for Vector API preview and ensure cache configuration is
  stable across OS targets.

### Phase 2: Core math kernel + circuit parser (OpenQASM 3 / JSON)
Current status: parser pipeline implemented for JSON and a limited OpenQASM 3
subset, with CLI file ingestion and parser-to-simulation tests in place.

Remaining work:
- Expand OpenQASM grammar coverage (additional gates, expressions, registers).
- Extend the JSON schema as new operations and metadata are introduced.
- Document grammar coverage, supported constructs, and known limitations.

### Phase 3: Statevector MVP + algorithm mini suite v1
Current status: statevector engine and a small CLI suite (Bell, GHZ, QFT) are
implemented with tests.

Remaining work:
- Expand the algorithm suite to include Deutsch-Jozsa, Bernstein-Vazirani, and
  phase estimation, with expected outcomes documented and tested.
- Add performance sanity checks for larger qubit counts (<= 30) and deeper
  circuits to validate the MVP scope.
- Capture a formal progress report (Phase 3 deliverable) that summarizes
  correctness, supported operations, and performance notes.

### Phase 4: Noise layer
Current status: `StatevectorEngine` applies `NoiseModel` channels after each
unitary gate using Kraus sampling. Noise configuration is currently API-only
via `SimulationRequest`.

Remaining work:
- Add CLI flags and config loading helpers for building `NoiseModel` instances.
- Extend noise support to multi-qubit Kraus operators and idle-time decoherence.
- Add trajectory averaging helpers for density-matrix style statistics.
