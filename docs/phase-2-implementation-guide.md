# Phase 2 Implementation Guide (Junior Developer)

This guide translates the Phase 2 remaining work into a clear, step-by-step
implementation plan. The goal is to deliver a working circuit ingest pipeline
that parses OpenQASM 3 and a JSON format into `QuantumCircuit`, and then runs
those circuits through a simulator engine (statevector today).

## Scope and goals
- Implement a parser pipeline: source text -> `QuantumCircuit`.
- Support two inputs:
  - OpenQASM 3 (subset defined below).
  - JSON circuit format (defined below).
- Provide an end-to-end path from file input to simulation.
- Add tests that exercise parse -> simulate for small circuits.

Non-goals for Phase 2:
- No noise integration yet.
- No mid-circuit measurement collapse.
- No alternative engines beyond the statevector backend.

## Deliverables checklist
- Parser API and implementation classes in `core/`.
- JSON parser with a documented schema.
- OpenQASM 3 subset parser with documented limitations.
- CLI subcommand to load and run a circuit file.
- Unit and integration tests for parsing and simulation.
- Documentation updates for supported formats and known limits.

## Step-by-step instructions

### 1) Define a parser API in core
Create a small interface so parsers are pluggable:

- Package: `com.omaarr90.statecraft.core.parse`
- Interface:
  - `QuantumCircuit parse(String source)`
  - optional: `QuantumCircuit parse(Path path)` helper
- Add a small error type (ex: `CircuitParseException`) that carries a message
  and optional line/column data.

Why: we want a consistent entry point for both JSON and QASM, and a place to
add future formats.

### 2) Decide on a JSON schema and implement the JSON parser
Define a simple schema that maps cleanly to `QuantumCircuit` operations. Use
Jackson (recommended) for parsing to keep the implementation short.

Suggested JSON schema (keep this exact if possible):

```
{
  "qubits": 2,
  "operations": [
    { "gate": "h", "target": 0 },
    { "gate": "cx", "control": 0, "target": 1 },
    { "gate": "measure", "qubits": [0, 1] }
  ]
}
```

Required mapping to `QuantumCircuit`:
- `h`, `x`, `y`, `z` -> `append(new Hadamard/PauliX/PauliY/PauliZ, target)`
- `cx` -> `append(CnotGate.of(), control, target)`
- `swap` -> `appendSwap(first, second)`
- `cp` -> `appendControlledPhase(angle, control, target)`
- `measure` -> `measure(qubits...)`

Validation rules:
- `qubits` must be > 0.
- All qubit indices must be within range.
- Reject unknown gate names with a clear error message.
- If a measurement appears, do not allow gates after it (engine limitation).

Implementation notes:
- Add Jackson dependency in `core/build.gradle.kts`.
- Keep parsing logic separate from validation logic; errors should be explicit.

### 3) Implement an OpenQASM 3 subset parser
Implement a small tokenizer + parser for a limited, well-documented subset.
Do not try to cover the full OpenQASM 3 grammar. Start with these statements:

Supported header and declarations:
- `OPENQASM 3.0;`
- `qubit[n] q;` (one register named `q` only)
- `bit[n] c;` (optional, can be ignored)

Supported instructions:
- `h q[i];`
- `x q[i];`
- `y q[i];`
- `z q[i];`
- `cx q[i], q[j];`
- `swap q[i], q[j];`
- `cp(angle) q[i], q[j];` where `angle` is a numeric literal or `pi` or
  `pi/2` or `pi/4` (support simple `pi` divisions at minimum)
- `measure q[i];` or `measure q[i] -> c[j];` (ignore classical target)

Validation rules:
- Require the `qubit[...] q;` declaration and use its size for validation.
- Reject any gate that references undefined registers or out-of-range qubits.
- Enforce measurements as a suffix (no unitary gate after any measure).

Implementation notes:
- Keep the tokenizer simple: recognize identifiers, integers, decimals,
  punctuation, and the keyword `pi`.
- Implement a small helper to parse angles:
  - support literals like `1.5708`
  - support `pi`, `pi/2`, `pi/4`, `pi/8`
- If the syntax is not supported, throw `CircuitParseException` with line/col.

### 4) Build a format detection layer
Add a small helper that picks a parser based on:
- explicit CLI option (`--format qasm|json`), or
- file extension (`.qasm`, `.qasm3`, `.json`), or
- content prefix (`OPENQASM` for QASM).

Keep this logic in a single place so future formats are easy to add.

### 5) Add a CLI subcommand to run circuit files
Extend `StatecraftCli` (in `app/`) with a new subcommand, e.g. `run`:

Command options (suggested):
- `--input <path>` (required)
- `--format <qasm|json|auto>` (default: auto)
- `--engine <id>` (default: statevector)
- `--shots <n>` (optional, same behavior as `demo`)
- `--seed <n>` and `--samples` (only valid when shots > 0)

Behavior:
- Parse the circuit file into a `QuantumCircuit`.
- Build a `SimulationRequest` with optional measurement instruction.
- Use `ServiceLoader` to resolve the requested engine.
- Print amplitudes and/or histograms using existing helpers.

### 6) Tests
Add both parser-level and integration tests.

Suggested tests:
- JSON parser:
  - Bell circuit parses and simulates correctly.
  - invalid gate names are rejected.
- QASM parser:
  - Bell circuit (H + CX) parses and simulates correctly.
  - a circuit with measurement in the middle is rejected.
- Integration:
  - parse -> `StatevectorEngine.simulate(...)` -> compare against
    `QuantumCircuit.apply()` for a known small circuit.

Place tests in:
- `core/src/test/java/...` for parser logic.
- `engines/src/test/java/...` or `app/src/test/java/...` for integration.

### 7) Documentation updates
Update the following:
- `docs/README.md` with a brief note about the new parser and CLI usage.
- A short section in `docs/project-overview.md` describing the supported
  OpenQASM subset and JSON schema.

## Acceptance criteria
- A JSON file and a QASM file for the Bell circuit can be parsed and run via
  the CLI without manual code changes.
- Tests pass with `./gradlew test`.
- Unsupported syntax produces clear, actionable error messages.
- The parser code is self-contained, documented, and easy to extend.

## Example inputs

Example QASM (Bell):
```
OPENQASM 3.0;
qubit[2] q;
bit[2] c;

h q[0];
cx q[0], q[1];
measure q[0] -> c[0];
measure q[1] -> c[1];
```

Example JSON (Bell):
```
{
  "qubits": 2,
  "operations": [
    { "gate": "h", "target": 0 },
    { "gate": "cx", "control": 0, "target": 1 },
    { "gate": "measure", "qubits": [0, 1] }
  ]
}
```

## Helpful tips
- Keep parsing logic separate from circuit construction so validation errors
  are easier to locate.
- Use `CircuitParseException` consistently; avoid bare `RuntimeException`.
- When in doubt, prefer strict validation and clear error messages over
  permissive parsing.
