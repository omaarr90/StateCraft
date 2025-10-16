# StateCraft Progress Report

This document captures the current implementation status of the StateCraft quantum circuit simulator. It summarizes the architecture, functional pieces, and supporting assets that exist in the repository today.

## Repository Structure

- `core/`: Core library providing math primitives and quantum circuit logic.
- `engines/`: Placeholder module for future simulator engines that will implement the shared service interface.
- `app/`: Command-line interface packaged with GraalVM native-image support.
- `docs/`: Architecture decisions, planning deliverables, and this progress report.

## Core Library

### Math Primitives

- `ComplexNumber` (record): immutable complex scalar with arithmetic helpers (`plus`, `minus`, `times`, `scale`, `conjugate`, `magnitudeSquared`) and cached `zero()`/`one()` instances.
- `ComplexArrays`: BLAS-style operations on interleaved `[re0, im0, re1, im1, ...]` buffers, including:
  - Norm calculations (`norm2`, `norm2Sq`) with robust scaling to avoid overflow.
  - `scal` for complex scaling in place.
  - `axpy` for fused multiply-add that tolerates overlapping slices.
  - `mul` for element-wise complex multiplication supporting aliasing.
  - Comprehensive argument validation (null, bounds, alignment) with IEEE-754 semantics.
- Unit tests cover correctness, boundary conditions, NaN/Infinity propagation, and aliasing scenarios (`ComplexArraysTest`, `ComplexArraysTinyOpsTest`).

### Quantum Circuit Model

- `SingleQubitGate` sealed interface and concrete Pauli X/Y/Z plus Hadamard gate records with precomputed matrix elements.
- `QuantumCircuit` record representing an ordered list of single-qubit gate applications; includes validation of qubit ranges and lazy application to state vectors.
- `StateVector` record encapsulating immutable amplitude snapshots backed by interleaved double arrays; exposes indexed accessors and cloning safeguards.
- Tests verify gate matrices, circuit evolution on 1- and 2-qubit examples, and phase behavior (`SingleQubitGateTest`, `QuantumCircuitTest`).

## Engine Abstraction

- `SimulatorEngine` interface defines the contract (`id()` plus `simulate`) that concrete simulation back-ends implement and expose via `ServiceLoader`.
- `StatevectorEngine` (in `engines` module) now drives SIMD-accelerated kernels directly over the canonical AoS `[re, im]` buffer, eliminating the legacy real/imag split staging arrays. The refactor tightened `StatevectorKernels` signatures around a single `double[]` and added vector-friendly shuffle helpers so gather/scatter stays localized.
- A manual microbenchmark (`StatevectorKernelMicrobenchmark` under `engines/src/test/java`) contrasts the AoS kernels with a split-buffer reference to track throughput deltas. Run it with `java --enable-preview --add-modules jdk.incubator.vector -cp engines/build/classes/java/main:engines/build/classes/java/test com.omaarr90.statecraft.engines.statevector.StatevectorKernelMicrobenchmark`.
- Running tests or the CLI with this engine still requires `--enable-preview --add-modules jdk.incubator.vector`.

## Command-Line Interface

- `StatecraftCli` uses Picocli to expose a `statecraft` command with an `engines` subcommand.
- The CLI demo subcommand now resolves the `statevector` engine, runs the Bell-state circuit through it, and pretty-prints the non-zero amplitudes.
- A hard-coded `suite` subcommand executes a mini algorithm catalog (Bell pair, GHZ, 3-qubit QFT) and prints both amplitude breakdowns and deterministic measurement histograms.
- Shot sampling flags (`--shots`, `--seed`, `--samples`) request histograms or raw outcomes alongside amplitudes, making it easy to experiment with shot-based workflows.
- Build script enables GraalVM native image generation (`org.graalvm.buildtools.native` plugin) with autodetected resources.

## Build, Tooling, and Quality Gates

- Multi-project Gradle build targeting Java 25 toolchain for all subprojects.
- Spotless plugin configured globally (runs on `./gradlew spotlessCheck`).
- Testing uses JUnit Jupiter 5.12 with platform launcher for IDE compatibility; `./gradlew test` exercises core and app modules.
- Dependency versions centralized in `gradle/libs.versions.toml`.

## Documentation and Planning Assets

- Architecture Decision Record `docs/core/statevector-layout.md` documents chosen statevector bit order, complex precision, and AoS layout.
- Measurement semantics for shot sampling and CLI integration are described in `docs/core/statevector-measurements.md`.
- Project proposal (`docs/deliverables/project-proposal.md` and PDF variant) outlines long-term roadmap, milestones, and engine strategy.

## Current Gaps and Next Steps

- Statevector engine currently covers single-qubit gates and CNOT; additional multi-qubit primitives, noise, and alternative back-ends are still pending.
- Circuit ingest (OpenQASM/JSON), benchmarking harnesses, and noise modeling remain future work.
- Additional documentation will be needed as engines and parsers are introduced.

## How to Reproduce the Current State

```sh
./gradlew test
```

The command runs all unit tests and verifies the code compiles across the multi-module build. Spotless can be invoked via `./gradlew spotlessCheck` to enforce formatting prior to commits.
