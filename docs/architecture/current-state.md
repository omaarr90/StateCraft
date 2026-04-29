# StateCraft Current State

StateCraft is a multi-module Java quantum circuit simulator.

## Repository Layout

| Module | Purpose |
| --- | --- |
| `core/` | Circuit model, parsers, math primitives, measurement API, and noise model. |
| `engines/` | Simulator backends exposed through Java `ServiceLoader`. |
| `app/` | Picocli command-line interface and native-image packaging. |
| `docs/` | User guides, architecture notes, and generated docs site sources. |

## Core Capabilities

- Immutable circuit construction with built-in gates, controlled operations,
  multi-control builders, SWAP, diagonal two-qubit gates, and terminal
  measurement metadata.
- JSON and OpenQASM parsing for common circuit files.
- Dense `StateVector` snapshots with little-endian qubit indexing.
- Measurement requests and result types for histograms and raw samples.
- Noise model APIs for depolarizing, amplitude damping, phase flip, phase
  damping, thermal relaxation, and composite channels.

## Engine Capabilities

| Engine | Summary |
| --- | --- |
| `statevector` | Dense exact simulator with SIMD-oriented kernels, parallel execution, measurement, and noise support. |
| `stabilizer` | Aaronson-Gottesman tableau simulator for Clifford-focused workloads. |
| `tensornetwork` | Matrix Product State simulator for shallow circuits on larger qubit counts. |

## Build and CI

The project uses a Gradle multi-project build with Java 25, JUnit 5, Spotless,
Javadocs, Maven publication tasks, and GitHub Actions workflows for CI,
release publishing, and documentation deployment.
