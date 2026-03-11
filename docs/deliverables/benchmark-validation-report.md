# Benchmark and Validation Report

## Run Metadata
- Run timestamp (local): 2026-03-11 05:46:27 +03:00
- Run timestamp (UTC): 2026-03-11 02:46:27 UTC
- Timezone: Arab Standard Time (offset 03:00:00)
- Git branch: main
- Git commit: fd7e5b4b87405007170f180661b9c788c09ccdbd
- OS: Microsoft Windows 11 Pro (10.0.26200)
- CPU: 11th Gen Intel(R) Core(TM) i9-11900KF @ 3.50GHz
- CPU topology: 8 cores / 16 logical processors
- RAM: 31.86 GiB
- Java: openjdk version "25" 2025-09-16 LTS
- Gradle: Gradle 9.0.0

## Benchmark Configuration
- Iterations: 5
- Compile command: `.\gradlew.bat :engines:compileTestJava --console=plain`
- Benchmark command: `java --enable-preview --add-modules jdk.incubator.vector --class-path "engines\build\classes\java\main;engines\build\classes\java\test;core\build\classes\java\main" com.omaarr90.statecraft.engines.statevector.StatevectorKernelMicrobenchmark`
- Classpath: `engines\build\classes\java\main;engines\build\classes\java\test;core\build\classes\java\main`

## Benchmark Runs
| Run | AoS (ms) | Split (ms) | Speedup (x) | Max abs diff |
| --- | ---: | ---: | ---: | ---: |
| 1 | 1369.020 | 4412.591 | 3.220 | 0.000e+00 |
| 2 | 1330.461 | 5492.103 | 4.130 | 0.000e+00 |
| 3 | 1375.556 | 4559.551 | 3.310 | 0.000e+00 |
| 4 | 1315.992 | 5226.200 | 3.970 | 0.000e+00 |
| 5 | 1328.711 | 5007.135 | 3.770 | 0.000e+00 |

## Benchmark Summary
| Metric | Mean | Min | Max | Stddev |
| --- | ---: | ---: | ---: | ---: |
| AoS (ms) | 1343.948 | 1315.992 | 1375.556 | 23.762 |
| Split (ms) | 4939.516 | 4412.591 | 5492.103 | 403.518 |
| Speedup (x) | 3.680 | 3.220 | 4.130 | 0.359 |

- Worst max abs diff: 0.000e+00

## Validation Summary
- Validation command: `.\gradlew.bat test --console=plain`
- Suites: 23
- Tests: 192
- Failures: 0
- Errors: 0
- Skipped: 0
- Total time (from JUnit XML): 1.810 s

## Validation by Module
| Module | Suites | Tests | Failures | Errors | Skipped | Time (s) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| app | 2 | 16 | 0 | 0 | 0 | 0.656 |
| core | 12 | 106 | 0 | 0 | 0 | 0.372 |
| engines | 9 | 70 | 0 | 0 | 0 | 0.782 |

## Validation by Suite
| Module | Suite | Tests | Failures | Errors | Skipped | Time (s) |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| app | com.omaarr90.statecraft.NoiseOptionsSupportTest | 6 | 0 | 0 | 0 | 0.390 |
| app | com.omaarr90.statecraft.StatecraftCliTest | 10 | 0 | 0 | 0 | 0.266 |
| core | com.omaarr90.statecraft.core.engine.MeasurementInstructionTest | 6 | 0 | 0 | 0 | 0.034 |
| core | com.omaarr90.statecraft.core.engine.MeasurementResultTest | 2 | 0 | 0 | 0 | 0.009 |
| core | com.omaarr90.statecraft.core.engine.SimulationRequestTest | 9 | 0 | 0 | 0 | 0.032 |
| core | com.omaarr90.statecraft.core.math.ComplexArraysTest | 11 | 0 | 0 | 0 | 0.014 |
| core | com.omaarr90.statecraft.core.math.ComplexArraysTinyOpsTest | 13 | 0 | 0 | 0 | 0.012 |
| core | com.omaarr90.statecraft.core.noise.ErrorChannelTest | 21 | 0 | 0 | 0 | 0.039 |
| core | com.omaarr90.statecraft.core.noise.NoiseModelTest | 15 | 0 | 0 | 0 | 0.037 |
| core | com.omaarr90.statecraft.core.parse.JsonCircuitParserTest | 5 | 0 | 0 | 0 | 0.165 |
| core | com.omaarr90.statecraft.core.parse.OpenQasmCircuitParserTest | 5 | 0 | 0 | 0 | 0.016 |
| core | com.omaarr90.statecraft.quantum.QuantumCircuitTest | 10 | 0 | 0 | 0 | 0.006 |
| core | com.omaarr90.statecraft.quantum.SingleQubitGateTest | 6 | 0 | 0 | 0 | 0.004 |
| core | com.omaarr90.statecraft.quantum.StateVectorTest | 3 | 0 | 0 | 0 | 0.004 |
| engines | com.omaarr90.statecraft.engines.CrossEngineConformanceTest | 3 | 0 | 0 | 0 | 0.146 |
| engines | com.omaarr90.statecraft.engines.stabilizer.StabilizerEngineTest | 15 | 0 | 0 | 0 | 0.043 |
| engines | com.omaarr90.statecraft.engines.stabilizer.StabilizerRandomCliffordConformanceTest | 1 | 0 | 0 | 0 | 0.086 |
| engines | com.omaarr90.statecraft.engines.statevector.CircuitParsingIntegrationTest | 1 | 0 | 0 | 0 | 0.205 |
| engines | com.omaarr90.statecraft.engines.statevector.StatevectorAlgorithmSuiteTest | 4 | 0 | 0 | 0 | 0.008 |
| engines | com.omaarr90.statecraft.engines.statevector.StatevectorEngineTest | 23 | 0 | 0 | 0 | 0.237 |
| engines | com.omaarr90.statecraft.engines.statevector.StatevectorNoiseTest | 7 | 0 | 0 | 0 | 0.022 |
| engines | com.omaarr90.statecraft.engines.tensornetwork.MpsStateTest | 3 | 0 | 0 | 0 | 0.017 |
| engines | com.omaarr90.statecraft.engines.tensornetwork.TensorNetworkEngineTest | 13 | 0 | 0 | 0 | 0.018 |

## Reproducibility Commands
```powershell
.\scripts\benchmark\run-benchmark-validation.ps1 -Runs 5
.\gradlew.bat :engines:compileTestJava --console=plain
java --enable-preview --add-modules jdk.incubator.vector --class-path "engines\build\classes\java\main;engines\build\classes\java\test;core\build\classes\java\main" com.omaarr90.statecraft.engines.statevector.StatevectorKernelMicrobenchmark
.\gradlew.bat test --console=plain
.\gradlew.bat clean :app:nativeCompile --console=plain
.\app\build\native\nativeCompile\statecraft.exe demo
.\app\build\native\nativeCompile\statecraft.exe suite
$bellJson = Join-Path $env:TEMP "statecraft-bell.json"
@'
{
  "qubits": 2,
  "operations": [
    { "gate": "h", "target": 0 },
    { "gate": "cx", "control": 0, "target": 1 },
    { "gate": "measure", "qubits": [0, 1] }
  ]
}
'@ | Set-Content -Encoding Ascii $bellJson
.\app\build\native\nativeCompile\statecraft.exe run --input $bellJson --shots 32
```

