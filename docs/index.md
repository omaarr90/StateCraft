# StateCraft

StateCraft is a Java quantum circuit simulator with a shared circuit API,
parsers, noise models, measurement results, and multiple simulator engines.

Use the core module when you need circuit construction, parsing, math types,
measurement request types, or noise models. Add the engines module when you
want ready-to-run simulator backends.

## Modules

| Artifact | Purpose |
| --- | --- |
| `com.omaarr90.statecraft:statecraft-core` | Circuit model, parsers, state vectors, measurement API, and noise model API. |
| `com.omaarr90.statecraft:statecraft-engines` | Statevector, stabilizer, and tensor-network simulator backends. |

`statecraft-engines` depends on `statecraft-core`, so applications that use the
built-in engines usually only need the engines artifact.

## Quick Example

```java
import com.omaarr90.statecraft.core.engine.MeasurementInstruction;
import com.omaarr90.statecraft.core.engine.SimulationRequest;
import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import com.omaarr90.statecraft.core.engine.SimulatorEngines;
import com.omaarr90.statecraft.quantum.CnotGate;
import com.omaarr90.statecraft.quantum.Hadamard;
import com.omaarr90.statecraft.quantum.QuantumCircuit;

public final class BellExample {
    public static void main(String[] args) {
        QuantumCircuit bell = new QuantumCircuit(2)
            .append(new Hadamard(), 0)
            .append(CnotGate.of(), 0, 1);

        SimulatorEngine engine = SimulatorEngines.require("statevector");
        SimulationRequest request = SimulationRequest.zeroState(bell)
            .withMeasurement(MeasurementInstruction.countsAll(1024).withSeed(42L), false);

        var result = engine.simulate(request);
        System.out.println(result.measurement().orElseThrow());
    }
}
```

## Documentation

- [Getting Started](getting-started.md): install StateCraft and configure Java.
- [Core Module](core.md): build circuits, parse files, measure results, and add
  noise models.
- [Engine Module](engines.md): choose and run a simulator backend.
- [API Reference](api.md): generated Javadocs for `statecraft-core` and
  `statecraft-engines`.
