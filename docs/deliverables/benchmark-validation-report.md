# Benchmark and Validation Report

## Run Metadata
- Run timestamp (local): 2026-03-13 03:35:09 +03:00
- Run timestamp (UTC): 2026-03-13 00:35:09 UTC
- Timezone: Asia/Riyadh (offset +03:00)
- Git branch: main
- Git commit: 26ea3273008f3967848d9092fed1284a76092704
- OS: Windows 11 10.0
- CPU: Intel64 Family 6 Model 167 Stepping 1, GenuineIntel
- CPU topology: 8 cores / 16 logical processors
- RAM: 31.864 GiB
- Java: openjdk version "25" 2025-09-16 LTS
- Gradle: 9.0.0

## Benchmark Configuration
- Gradle task: `.\gradlew.bat benchmarkValidationReport --console=plain -PbenchmarkRuns=5 -PbenchmarkOutputDir=build/reports/benchmark-validation -PbenchmarkReportPath=docs/deliverables/benchmark-validation-report.md`
- Iterations: 5
- Benchmark JVM command: `java --enable-preview --add-modules jdk.incubator.vector --class-path "engines/build/classes/java/main;engines/build/classes/java/test;core/build/classes/java/main" com.omaarr90.statecraft.engines.statevector.StatevectorKernelMicrobenchmark`
- Classpath: `engines/build/classes/java/main;engines/build/classes/java/test;core/build/classes/java/main`

## Benchmark Runs
| Run | AoS (ms) | Split (ms) | Speedup (x) | Max abs diff |
| --- | ---: | ---: | ---: | ---: |
| 1 | 1319.666 | 3557.165 | 2.700 | 0.000e+00 |
| 2 | 1346.793 | 3725.272 | 2.770 | 0.000e+00 |
| 3 | 1320.235 | 3381.953 | 2.560 | 0.000e+00 |
| 4 | 1320.097 | 3761.638 | 2.850 | 0.000e+00 |
| 5 | 1301.421 | 3225.373 | 2.480 | 0.000e+00 |

## Benchmark Summary
| Metric | Mean | Min | Max | Stddev |
| --- | ---: | ---: | ---: | ---: |
| AoS (ms) | 1321.642 | 1301.421 | 1346.793 | 14.490 |
| Split (ms) | 3530.280 | 3225.373 | 3761.638 | 203.588 |
| Speedup (x) | 2.672 | 2.480 | 2.850 | 0.135 |

- Worst max abs diff: 0.000e+00

## Validation Summary
- Validation task inputs: `.\gradlew.bat test --console=plain`
- Suites: 23
- Tests: 196
- Failures: 0
- Errors: 0
- Skipped: 0
- Total time (from JUnit XML): 1.730 s

## Validation by Module
| Module | Suites | Tests | Failures | Errors | Skipped | Time (s) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| app | 2 | 16 | 0 | 0 | 0 | 0.582 |
| core | 12 | 110 | 0 | 0 | 0 | 0.328 |
| engines | 9 | 70 | 0 | 0 | 0 | 0.820 |

## Validation by Suite
| Module | Suite | Tests | Failures | Errors | Skipped | Time (s) |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| app | com.omaarr90.statecraft.NoiseOptionsSupportTest | 6 | 0 | 0 | 0 | 0.363 |
| app | com.omaarr90.statecraft.StatecraftCliTest | 10 | 0 | 0 | 0 | 0.219 |
| core | com.omaarr90.statecraft.core.engine.MeasurementInstructionTest | 6 | 0 | 0 | 0 | 0.028 |
| core | com.omaarr90.statecraft.core.engine.MeasurementResultTest | 2 | 0 | 0 | 0 | 0.007 |
| core | com.omaarr90.statecraft.core.engine.SimulationRequestTest | 9 | 0 | 0 | 0 | 0.025 |
| core | com.omaarr90.statecraft.core.math.ComplexArraysTest | 11 | 0 | 0 | 0 | 0.011 |
| core | com.omaarr90.statecraft.core.math.ComplexArraysTinyOpsTest | 13 | 0 | 0 | 0 | 0.013 |
| core | com.omaarr90.statecraft.core.noise.ErrorChannelTest | 25 | 0 | 0 | 0 | 0.042 |
| core | com.omaarr90.statecraft.core.noise.NoiseModelTest | 15 | 0 | 0 | 0 | 0.028 |
| core | com.omaarr90.statecraft.core.parse.JsonCircuitParserTest | 5 | 0 | 0 | 0 | 0.148 |
| core | com.omaarr90.statecraft.core.parse.OpenQasmCircuitParserTest | 5 | 0 | 0 | 0 | 0.010 |
| core | com.omaarr90.statecraft.quantum.QuantumCircuitTest | 10 | 0 | 0 | 0 | 0.008 |
| core | com.omaarr90.statecraft.quantum.SingleQubitGateTest | 6 | 0 | 0 | 0 | 0.005 |
| core | com.omaarr90.statecraft.quantum.StateVectorTest | 3 | 0 | 0 | 0 | 0.003 |
| engines | com.omaarr90.statecraft.engines.CrossEngineConformanceTest | 3 | 0 | 0 | 0 | 0.141 |
| engines | com.omaarr90.statecraft.engines.stabilizer.StabilizerEngineTest | 15 | 0 | 0 | 0 | 0.043 |
| engines | com.omaarr90.statecraft.engines.stabilizer.StabilizerRandomCliffordConformanceTest | 1 | 0 | 0 | 0 | 0.084 |
| engines | com.omaarr90.statecraft.engines.statevector.CircuitParsingIntegrationTest | 1 | 0 | 0 | 0 | 0.199 |
| engines | com.omaarr90.statecraft.engines.statevector.StatevectorAlgorithmSuiteTest | 4 | 0 | 0 | 0 | 0.004 |
| engines | com.omaarr90.statecraft.engines.statevector.StatevectorEngineTest | 23 | 0 | 0 | 0 | 0.288 |
| engines | com.omaarr90.statecraft.engines.statevector.StatevectorNoiseTest | 7 | 0 | 0 | 0 | 0.020 |
| engines | com.omaarr90.statecraft.engines.tensornetwork.MpsStateTest | 3 | 0 | 0 | 0 | 0.025 |
| engines | com.omaarr90.statecraft.engines.tensornetwork.TensorNetworkEngineTest | 13 | 0 | 0 | 0 | 0.016 |

## Reproducibility Commands
```bash
.\gradlew.bat benchmarkValidationReport --console=plain -PbenchmarkRuns=5 -PbenchmarkOutputDir=build/reports/benchmark-validation -PbenchmarkReportPath=docs/deliverables/benchmark-validation-report.md
```
