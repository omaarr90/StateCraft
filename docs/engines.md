# Engine Module

The `statecraft-engines` artifact provides simulator backends that implement
the `SimulatorEngine` interface from `statecraft-core`.

Engine discovery uses Java `ServiceLoader`, so application code can select a
backend by id without constructing engine classes directly.

```java
SimulatorEngine engine = SimulatorEngines.require("statevector");
SimulationResult result = engine.simulate(SimulationRequest.zeroState(circuit));
```

## Available Engines

| Engine id | Class | Best fit |
| --- | --- | --- |
| `statevector` | `StatevectorEngine` | Dense exact simulation, arbitrary supported gates, measurement, and noise. |
| `stabilizer` | `StabilizerEngine` | Larger Clifford circuits with efficient tableau simulation. |
| `tensornetwork` | `TensorNetworkEngine` | Shallow circuits on larger qubit counts using an MPS backend. |

List all engines visible to the current class loader:

```java
List<String> engineIds = SimulatorEngines.loadAll().stream()
    .map(SimulatorEngine::id)
    .toList();
```

## Statevector Engine

The statevector backend is a dense simulator backed by SIMD-oriented kernels and
a ForkJoin data-parallel layer.

```java
SimulatorEngine engine = new StatevectorEngine(
    StatevectorExecutionConfig.withParallelism(4)
);
```

Use `StatevectorExecutionConfig.withParallelism(1)` to force serial execution
for debugging or comparisons. The default constructor selects automatic
parallel execution settings.

Capabilities:

- Supports all circuit operations in the current model.
- Supports seeded measurement sampling.
- Supports noise models and seeded noise sampling.
- Materializes dense final states up to the engine limit.

## Stabilizer Engine

The stabilizer backend uses an Aaronson-Gottesman tableau for Clifford-focused
workloads.

Capabilities:

- Supports Clifford single-qubit gates, CNOT, SWAP, CZ-style diagonal gates,
  and single-control Pauli builders.
- Supports terminal measurement sampling.
- Can avoid final-state materialization for large shot-only runs.

Limits:

- Rejects non-Clifford arbitrary unitaries and multi-control gates with more
  than one control.
- Does not support noisy simulation yet.
- Does not materialize final amplitudes above 20 qubits.

## Tensor-Network Engine

The tensor-network backend uses a Matrix Product State representation with
SVD-based compression.

Capabilities:

- Supports single-qubit gates, CNOT, diagonal two-qubit gates, SWAP, and
  terminal measurement sampling.
- Supports shallow circuits up to 50 qubits and depth 40.
- Uses a default max bond dimension of 256 and singular-value cutoff of
  `1e-10`.

Limits:

- Does not support noisy simulation yet.
- Rejects arbitrary two-qubit unitary matrices and multi-control gates.
- Dense initial-state ingestion and final-state materialization are limited to
  20 qubits.

## Support Matrix

| Capability | statevector | stabilizer | tensornetwork |
| --- | --- | --- | --- |
| Clifford gates | Yes | Yes | Yes |
| Non-Clifford single-qubit matrix gates | Yes | No | Yes |
| CNOT and SWAP | Yes | Yes | Yes |
| Controlled phase or diagonal two-qubit gates | Yes | Clifford cases | Yes |
| Arbitrary two-qubit unitary | Yes | No | No |
| Multi-control gates | Yes | Limited | No |
| Terminal measurement | Yes | Yes | Yes |
| Noise model | Yes | No | No |
| Large dense final state | Up to dense statevector limit | Up to 20 qubits | Up to 20 qubits |

## Shot-Only Runs

For large circuits where final amplitudes are not needed, request measurement
and omit the final state.

```java
SimulationRequest request = SimulationRequest.zeroState(circuit)
    .withMeasurement(MeasurementInstruction.countsAll(4096).withSeed(7L), false);

MeasurementResult measurement = engine.simulate(request)
    .measurement()
    .orElseThrow();
```
