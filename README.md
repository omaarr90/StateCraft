# StateCraft

StateCraft is a Java quantum circuit simulator with a shared circuit API,
parsers, noise models, and multiple simulator engines.

## Packages

StateCraft publishes two Maven artifacts:

```kotlin
dependencies {
    implementation("com.omaarr90.statecraft:statecraft-core:0.1.0")
    implementation("com.omaarr90.statecraft:statecraft-engines:0.1.0")
}
```

Maven users can import the same coordinates:

```xml
<dependency>
  <groupId>com.omaarr90.statecraft</groupId>
  <artifactId>statecraft-engines</artifactId>
  <version>0.1.0</version>
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

## GitHub Packages

StateCraft can also publish the same Maven coordinates to GitHub Packages at:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/omaarr90/StateCraft")
        credentials {
            username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

Maven consumers can add the same repository:

```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/omaarr90/StateCraft</url>
</repository>
```

GitHub Packages requires credentials to read packages. For local use, provide a
classic token with `read:packages`; GitHub Actions consumers can use a
`GITHUB_TOKEN` that has package read access.

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

## Development

Common checks:

```sh
./gradlew spotlessCheck test :core:javadoc :engines:javadoc publishToMavenLocal
./gradlew -p scripts/consumer-smoke test
```

Release publishing uses the Central Portal workflow in GitHub Actions and
requires Maven Central user-token credentials plus an in-memory GPG signing key.
The same workflow can publish to GitHub Packages using `GITHUB_TOKEN` with
`packages: write`.
