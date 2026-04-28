# StateCraft Current State

StateCraft is a multi-module Java quantum circuit simulator with three engines:
`statevector`, `stabilizer`, and `tensornetwork`. The repository is organized as:

- `core/`: circuit model, parsers, math primitives, measurement API, and noise model.
- `engines/`: simulator backends exposed through `ServiceLoader`.
- `app/`: Picocli CLI plus GraalVM native-image packaging.
- `docs/`: architecture notes, overview documents, and project deliverables.

## Circuit model and parsing

`QuantumCircuit` supports:

- single-qubit gates: `h`, `x`, `y`, `z`, `s`, `sdg`, plus generic matrix-backed single-qubit gates
- two-qubit operations: `cx`, controlled phase (`cp`), arbitrary diagonal two-qubit gates, and `swap`
- controlled-Pauli builders: `appendControlledX/Y/Z(...)`
- multi-control builders: `appendToffoli(...)` and `appendMultiControl(...)`
- measurement metadata as a terminal circuit suffix

The JSON parser accepts `h`, `x`, `y`, `z`, `s`, `sdg`, `cx`, `cy`, `cz`, `swap`, `cp`, `ccx`, `mcx`, `mcy`,
`mcz`, `measure`, and `barrier`.

The OpenQASM parser accepts common `OPENQASM 2.0;` and `OPENQASM 3.0;` subsets with:

- `qreg`/`creg` declarations, `qubit[n]`/`bit[n]` declarations, arbitrary register names, and multiple registers
- no-op `include "qelib1.inc";`/standard include handling for common exported files
- gates `h`, `x`, `y`, `z`, `s`, `sdg`, `t`, `tdg`, `id`, `p`, `u`, `U`, `u1`, `u2`, `u3`, `rx`, `ry`, `rz`,
  `cx`, `cy`, `cz`, `ccx`, `mcx`, `mcy`, `mcz`, `swap`, `cp(angle)`, and `cu1(angle)`
- `barrier`, including register-wide barriers
- `measure q[i] -> c[i];`, `measure q -> c;`, and register-wide terminal measurement

Parser ingress tolerates a leading UTF-8 BOM on circuit files. QASM angles accept standard scientific notation and
constant expressions over `pi`, unary signs, `+`, `-`, `*`, `/`, and parentheses.

## Engines

`statevector`

- dense statevector simulator backed by Vector API kernels
- ForkJoin data-parallel execution for large statevector gate kernels, with
  `--statevector-parallelism <threads>` available in the CLI
- supports all circuit operations in the current model
- supports seeded measurement sampling and noise application after each unitary gate

`stabilizer`

- Clifford-focused simulator backed by an Aaronson-Gottesman tableau
- supports Clifford single-qubit gates, `cx`, `swap`, CZ-style diagonal gates, and single-control Pauli builders
- rejects arbitrary two-qubit unitaries, multi-control gates with more than one control, and noisy simulation

`tensornetwork`

- MPS backend for shallow circuits
- supports single-qubit gates, `cx`, diagonal two-qubit gates, `swap`, and measurement suffixes
- rejects arbitrary two-qubit unitaries, multi-control gates, and noisy simulation

## Noise support

The core noise model supports:

- depolarizing
- amplitude damping
- phase flip
- phase damping
- thermal relaxation (`t1`, `t2`, `gateTime`)
- composite channels
- gate-type, idle-qubit, and global scheduling through `NoiseModel`

`StatevectorEngine` applies scheduled noise after each unitary gate. Noise sampling can be made deterministic with a
noise seed, and Kraus branches are sampled from the current state instead of fixed per-operator weights. A noise seed
without any configured noise channels is rejected, including seed-only JSON noise configs.

## CLI

The CLI entrypoint is `statecraft` with four subcommands:

- `statecraft engines`: list discovered engine ids
- `statecraft demo`: run the built-in Bell-state demo
- `statecraft run --input <file>`: parse and simulate a JSON or QASM circuit
- `statecraft suite`: execute the built-in Bell, GHZ, Deutsch-Jozsa, Bernstein-Vazirani, QFT, and 1-bit phase-estimation sample suite

`demo` and `run` support measurement flags:

- `--shots`
- `--seed`
- `--samples`
- `--omit-final-state`

`run` also supports:

- `--format qasm|json|auto`
- `--engine <id>`

`demo`, `run`, and `suite` all accept noise options:

- `--noise-config <json>`
- `--noise-seed <long>`
- `--noise-depolarizing <p>`
- `--noise-amplitude-damping <gamma>`
- `--noise-phase-flip <p>`
- `--noise-phase-damping <lambda>`
- `--noise-thermal-t1 <seconds>`
- `--noise-thermal-t2 <seconds>`
- `--noise-thermal-gate-time <seconds>`

CLI noise config files may carry a leading UTF-8 BOM. CLI flags and config values are merged, and CLI `--noise-seed`
overrides `noiseSeed` from the config file.

## Build, CI, and formatting

- Gradle multi-project build using the Java 25 toolchain
- JUnit 5 test suites across `core`, `engines`, and `app`
- Spotless enforcement for Java, Gradle Kotlin DSL, and basic Markdown or `.gitignore` whitespace hygiene
- GitHub Actions CI:
  - matrix build on `ubuntu-latest`, `macos-latest`, and `windows-latest`
  - Ubuntu native-image job running `:app:nativeCompile`

Common local commands:

```sh
./gradlew spotlessCheck test
./gradlew :app:nativeCompile
```
