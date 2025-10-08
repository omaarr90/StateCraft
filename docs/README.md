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

- `SimulatorEngine` interface defines the contract (`id()`) that concrete simulation back-ends will implement and register via `ServiceLoader` in later phases.
- `engines` module is included in the Gradle build and depends on `core`, ready to host implementations.

## Command-Line Interface

- `StatecraftCli` uses Picocli to expose a `statecraft` command with an `engines` subcommand.
- The current CLI discovers `SimulatorEngine` implementations via `ServiceLoader`, prints any registered IDs, and communicates that engines are pending when none are found.
- Build script enables GraalVM native image generation (`org.graalvm.buildtools.native` plugin) with autodetected resources.

## Build, Tooling, and Quality Gates

- Multi-project Gradle build targeting Java 25 toolchain for all subprojects.
- Spotless plugin configured globally (runs on `./gradlew spotlessCheck`).
- Testing uses JUnit Jupiter 5.12 with platform launcher for IDE compatibility; `./gradlew test` exercises core and app modules.
- Dependency versions centralized in `gradle/libs.versions.toml`.

## Documentation and Planning Assets

- Architecture Decision Record `docs/core/statevector-layout.md` documents chosen statevector bit order, complex precision, and AoS layout.
- Project proposal (`docs/deliverables/project-proposal.md` and PDF variant) outlines long-term roadmap, milestones, and engine strategy.

## Current Gaps and Next Steps

- No simulator engines implemented yet; CLI reports none discovered.
- Circuit model handles single-qubit gates only; multi-qubit gates and noise channels remain future work.
- Additional documentation will be needed as engines and parsers are introduced.

## How to Reproduce the Current State

```sh
./gradlew test
```

The command runs all unit tests and verifies the code compiles across the multi-module build. Spotless can be invoked via `./gradlew spotlessCheck` to enforce formatting prior to commits.
