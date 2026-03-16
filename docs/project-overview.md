# StateCraft Project Overview

## Summary

StateCraft is a Java quantum circuit simulator with a shared circuit and parser layer, multiple execution engines, and
a Picocli CLI packaged for GraalVM native-image. The repository currently ships:

- a dense `statevector` engine for the full supported gate set
- a `stabilizer` engine for Clifford-compatible workloads
- a `tensornetwork` engine for shallow larger circuits

## Repository layout

- `core/`: circuit types, parsers, math primitives, measurement API, and noise modeling
- `engines/`: backend implementations and cross-engine tests
- `app/`: CLI application and native-image build configuration
- `docs/`: architecture notes, overviews, and deliverables

## Circuit and parser support

### Circuit model

`QuantumCircuit` supports:

- single-qubit gates `h`, `x`, `y`, `z`, `s`, `sdg`
- `cx`
- controlled phase via diagonal two-qubit operations
- `swap`
- arbitrary two-qubit unitaries
- single-control Pauli builders (`appendControlledX/Y/Z`)
- general multi-control builders (`appendMultiControl`, `appendToffoli`)
- measurement operations as a terminal suffix

### JSON format

The JSON parser supports:

- `h`, `x`, `y`, `z`, `s`, `sdg`
- `cx`, `cy`, `cz`
- `swap`
- `cp`
- `ccx`, `mcx`, `mcy`, `mcz`
- `measure`
- `barrier`

### OpenQASM format

The OpenQASM parser currently supports an `OPENQASM 3.0;` subset with:

- `qubit[n] q;`
- optional `bit[n] c;`
- `h`, `x`, `y`, `z`, `s`, `sdg`
- `cx`, `cy`, `cz`, `ccx`
- `swap`
- `cp(angle)`
- `barrier`
- `measure q[i] -> c[i];`
- register-wide `measure q;`

The parser accepts scientific-notation angles and tolerates a leading UTF-8 BOM on circuit files.

## Engine capabilities

### Statevector engine

- full support for the current circuit model
- seeded measurement sampling
- seeded noise application after each unitary gate
- dense mode limit of 29 qubits

### Stabilizer engine

- supports Clifford single-qubit gates, `cx`, `swap`, CZ-style diagonal gates, and single-control Pauli builders
- supports measurement sampling and computational-basis initialization
- rejects arbitrary two-qubit unitaries, noisy simulation, and multi-control gates with more than one control

### Tensor-network engine

- MPS-based backend for shallow circuits
- supports single-qubit gates, `cx`, diagonal two-qubit gates, `swap`, and terminal measurement
- rejects arbitrary two-qubit unitaries, multi-control gates, and noisy simulation

## Noise model

The core noise layer provides:

- depolarizing
- amplitude damping
- phase flip
- phase damping
- thermal relaxation
- channel composition
- scheduling by gate type, idle qubit, and global application

Only the `statevector` engine executes noisy simulations today. Noise seeds are supported through
`SimulationRequest.withNoiseSeed(...)` and through the CLI. A seed without any noise channels is treated as invalid,
including seed-only JSON noise configs.

## CLI

The `statecraft` CLI exposes:

- `engines`
- `demo`
- `run`
- `suite`

`demo` and `run` support:

- `--shots`
- `--seed`
- `--samples`
- `--omit-final-state`

`run` additionally supports:

- `--input`
- `--format qasm|json|auto`
- `--engine`

`demo`, `run`, and `suite` all share the noise mixin:

- `--noise-config`
- `--noise-seed`
- `--noise-depolarizing`
- `--noise-amplitude-damping`
- `--noise-phase-flip`
- `--noise-phase-damping`
- `--noise-thermal-t1`
- `--noise-thermal-t2`
- `--noise-thermal-gate-time`

Config-file and flag-based noise inputs are merged, with CLI `--noise-seed` taking precedence over `noiseSeed` from
the config.

## Build, formatting, and CI

- Java 25 toolchain across subprojects
- JUnit 5 test suites in every module
- Spotless enforcement for Java, Gradle Kotlin DSL, Markdown, and `.gitignore`
- GitHub Actions build matrix on Linux, macOS, and Windows
- dedicated Ubuntu native-image job running `./gradlew :app:nativeCompile --stacktrace`

Recommended local verification:

```sh
./gradlew spotlessCheck test
```
