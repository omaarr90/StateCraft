# Getting Started

StateCraft is published as Maven artifacts for Java projects. The current
release version in this repository is `1.0.0`.

## Requirements

- Java 25 toolchain.
- Preview features enabled when compiling or running code that uses
  `statecraft-engines`.
- The incubating Vector API module enabled for the engines module:
  `jdk.incubator.vector`.

## Gradle

Use `statecraft-engines` when you want the built-in backends. It brings in
`statecraft-core` transitively.

```kotlin
dependencies {
    implementation("com.omaarr90.statecraft:statecraft-engines:1.0.0")
}
```

Configure Java flags for compile, test, and runtime tasks that execute engine
code:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(
        listOf("--enable-preview", "--add-modules", "jdk.incubator.vector")
    )
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview", "--add-modules", "jdk.incubator.vector")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview", "--add-modules", "jdk.incubator.vector")
}
```

If you only need circuit construction, parsing, or data types, depend on core
directly:

```kotlin
dependencies {
    implementation("com.omaarr90.statecraft:statecraft-core:1.0.0")
}
```

## Maven

```xml
<dependency>
  <groupId>com.omaarr90.statecraft</groupId>
  <artifactId>statecraft-engines</artifactId>
  <version>1.0.0</version>
</dependency>
```

Configure preview and Vector API flags in your compiler, test, and runtime
plugins:

```xml
<compilerArgs>
  <arg>--enable-preview</arg>
  <arg>--add-modules</arg>
  <arg>jdk.incubator.vector</arg>
</compilerArgs>
<argLine>--enable-preview --add-modules jdk.incubator.vector</argLine>
```

## GitHub Packages

StateCraft can also be published to GitHub Packages. GitHub Packages requires
credentials even for reads.

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

Use a token with `read:packages` locally, or a `GITHUB_TOKEN` with package read
access in GitHub Actions.

## First Simulation

```java
QuantumCircuit circuit = new QuantumCircuit(1)
    .append(new Hadamard(), 0);

SimulatorEngine engine = SimulatorEngines.require("statevector");
SimulationRequest request = SimulationRequest.zeroState(circuit);
SimulationResult result = engine.simulate(request);

System.out.println(result.finalState().orElseThrow().amplitude(0));
System.out.println(result.finalState().orElseThrow().amplitude(1));
```
