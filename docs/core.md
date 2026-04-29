# Core Module

The `statecraft-core` artifact contains the public data model shared by all
engines: circuits, gates, state vectors, parsers, measurement request and result
types, and noise model types.

## Build Circuits

`QuantumCircuit` is immutable. Each builder-style method returns a new circuit
with the appended operation.

```java
QuantumCircuit bell = new QuantumCircuit(2)
    .append(new Hadamard(), 0)
    .append(CnotGate.of(), 0, 1);
```

Supported circuit builders include:

| Method | Use |
| --- | --- |
| `append(SingleQubitGate, int)` | Add `Hadamard`, `PauliX`, `PauliY`, `PauliZ`, `SGate`, `SdgGate`, or a matrix-backed single-qubit gate. |
| `append(CnotGate, int, int)` | Add CNOT. |
| `appendControlledX/Y/Z(...)` | Add controlled Pauli gates. |
| `appendToffoli(...)` | Add a two-control X gate. |
| `appendMultiControl(...)` | Add a multi-control single-qubit gate. |
| `appendControlledPhase(...)` | Add a controlled phase gate. |
| `appendDiagonalTwoQubit(...)` | Add a custom diagonal two-qubit gate. |
| `appendTwoQubitUnitary(...)` | Add a custom 4-by-4 two-qubit unitary. |
| `appendSwap(...)` | Add SWAP. |
| `measure(...)` | Add terminal measurement metadata. |

Measurement operations are metadata for engines. `QuantumCircuit.apply(...)`
evaluates only unitary circuits and rejects circuits containing measurement
operations.

## State Vectors and Bit Order

`StateVector` stores amplitudes as interleaved doubles:

```text
[real0, imag0, real1, imag1, ...]
```

Qubit indexing is little-endian. Qubit `0` is the least significant bit in a
basis-state index.

| Basis state | q1 | q0 | Index |
| --- | --- | --- | --- |
| `|00>` | 0 | 0 | 0 |
| `|01>` | 0 | 1 | 1 |
| `|10>` | 1 | 0 | 2 |
| `|11>` | 1 | 1 | 3 |

Printed bitstrings use the human-readable most-significant-bit-left display
convention. For a two-qubit circuit, `"10"` means `q1 = 1` and `q0 = 0`.

## Parse Circuit Files

Use `CircuitParsers.parse(...)` to load JSON or OpenQASM input.

```java
QuantumCircuit circuit = CircuitParsers.parse(
    Path.of("bell.qasm"),
    CircuitFormat.AUTO
);
```

`CircuitFormat.AUTO` detects JSON or QASM from the file path and source. The
parser accepts common OpenQASM 2.0 and 3.0 subsets, including single-qubit
gates, CNOT, controlled Pauli gates, SWAP, controlled phase, barriers, and
terminal register measurements.

## Request Measurement

Measurement is requested through `SimulationRequest`, not by directly sampling
from `StateVector`.

```java
MeasurementInstruction instruction = MeasurementInstruction
    .countsAll(1024)
    .withSeed(42L);

SimulationRequest request = SimulationRequest.zeroState(bell)
    .withMeasurement(instruction, false);
```

Use `countsAll` or `counts` for histograms, and `samplesAll` or `samples` for
raw shot outcomes. The optional seed makes sampling reproducible.

Measurement results are returned as:

| Result type | Use |
| --- | --- |
| `MeasurementResult.Histogram` | Integer-keyed counts for measured widths up to 31 qubits. |
| `MeasurementResult.Samples` | Integer-keyed raw samples for measured widths up to 31 qubits. |
| `MeasurementResult.BitstringHistogram` | String-keyed counts for wider measurements. |
| `MeasurementResult.BitstringSamples` | String-keyed raw samples for wider measurements. |

## Configure Noise

Noise models schedule `ErrorChannel` instances after operations, globally, or
on idle qubits.

```java
NoiseModel noise = NoiseModel.builder()
    .afterGate(
        QuantumCircuit.Operation.SingleGateOperation.class,
        ErrorChannel.depolarizing(0.001, 0)
    )
    .afterAllGates(ErrorChannel.phaseDamping(0.0005, 0))
    .build();

SimulationRequest noisyRequest = SimulationRequest.zeroState(bell)
    .withNoiseModel(noise)
    .withNoiseSeed(123L);
```

Current built-in channels include depolarizing, amplitude damping, phase flip,
phase damping, thermal relaxation, and composite channels. Noisy simulation is
currently supported by the statevector engine.
