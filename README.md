# StateCraft

StateCraft is a Java quantum circuit simulator with a shared circuit API,
parsers, noise models, and multiple simulator engines.

## Packages

StateCraft publishes two Maven artifacts to Maven Central. Gradle consumers
should resolve them with `mavenCentral()`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.omaarr90.statecraft:statecraft-core:1.0.0")
    implementation("com.omaarr90.statecraft:statecraft-engines:1.0.0")
}
```

Maven users can import the same coordinates from Maven Central at
`https://repo.maven.apache.org/maven2`:

```xml
<dependency>
  <groupId>com.omaarr90.statecraft</groupId>
  <artifactId>statecraft-engines</artifactId>
  <version>1.0.0</version>
</dependency>
```

Maven consumers also need preview and Vector API flags in compiler plugins and
test/runtime launchers:

```xml
<compilerArgs>
  <arg>--enable-preview</arg>
  <arg>--add-modules</arg>
  <arg>jdk.incubator.vector</arg>
</compilerArgs>
<argLine>--enable-preview --add-modules jdk.incubator.vector</argLine>
```

`statecraft-engines` depends on `statecraft-core`, so application projects only
need the engines artifact when they want the default simulator backends.

## Java Runtime

StateCraft engines currently target Java 25 and use the incubating Vector API.
Compile and run consumers with:

```sh
--enable-preview --add-modules jdk.incubator.vector
```

Gradle example:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--enable-preview", "--add-modules", "jdk.incubator.vector"))
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview", "--add-modules", "jdk.incubator.vector")
}
```

## Example

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
        var request = SimulationRequest.zeroState(bell)
            .withMeasurement(MeasurementInstruction.countsAll(1024).withSeed(42L));

        var result = engine.simulate(request);
        System.out.println(result.measurement().orElseThrow());
    }
}
```

Available engine ids are `statevector`, `stabilizer`, and `tensornetwork`.

The `statevector` engine uses SIMD kernels and a ForkJoin data-parallel layer
for large dense state updates. CLI commands that run the statevector backend
accept `--statevector-parallelism <threads>`; use `1` to force serial
execution for debugging or comparison.

## Example Suite

The CLI suite combines textbook circuits with realistic engine-limit workloads:
Grover search, six-qubit QFT, a QAOA-style ring ansatz, 40-qubit line-cluster
sampling, a tensor-network depth probe, and seeded noisy GHZ sampling.

```sh
statecraft suite --engine statevector
statecraft suite --engine stabilizer
statecraft suite --engine tensornetwork
```

Each example either runs on the selected engine or prints
`Expected engine limit: ...`.

| Example family | statevector | stabilizer | tensornetwork |
| --- | --- | --- | --- |
| Clifford/Bell/GHZ/BV basics | supported | supported | supported |
| Non-Clifford QFT/QAOA | supported | expected limit | supported |
| Multi-control Grover phase | supported | expected limit | expected limit |
| 40-qubit cluster sampling | expected limit | supported | supported |
| Depth 41 probe | supported | supported | expected limit |
| Noisy GHZ sampling | supported | expected limit | expected limit |

## Development

Common checks:

```sh
./gradlew spotlessCheck test :core:javadoc :engines:javadoc publishToMavenLocal
./gradlew -p scripts/consumer-smoke test
```

Release publishing uses the Central Portal workflow in GitHub Actions and
requires Maven Central user-token credentials plus an in-memory GPG signing key.

Release versions are read from `VERSION_NAME` in `gradle.properties`, or from
the `v*` tag / manual workflow input in `.github/workflows/release.yml`. See
[docs/releasing.md](docs/releasing.md) for Maven Central namespace, signing, and
workflow setup.
