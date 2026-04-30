# Getting Started

StateCraft is published to Maven Central as Maven artifacts for Java projects.
The current release version in this repository is `1.0.0`.

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
repositories {
    mavenCentral()
}

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

Maven Central is enabled by default for standard Maven projects. If your build
declares repositories explicitly, keep Maven Central available:

```xml
<repositories>
  <repository>
    <id>central</id>
    <url>https://repo.maven.apache.org/maven2</url>
  </repository>
</repositories>
```

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
