# Benchmark and Validation Report

## Run Metadata
- Run timestamp (local): 2026-04-27 21:32:01 +03:00
- Run timestamp (UTC): 2026-04-27 18:32:01 UTC
- Timezone: Asia/Riyadh (offset +03:00)
- Git branch: main
- Git commit: 100f21edae74138700fbeeb30b9543e26d95d2a9
- OS: Mac OS X 26.3
- CPU: Apple M4 Max
- CPU topology: 14 cores / 14 logical processors
- RAM: 36.000 GiB
- Java: openjdk version "25.0.1" 2025-10-21
- Gradle: 9.0.0

## Benchmark Configuration
- Gradle task: `./gradlew benchmarkValidationReport --console=plain -PbenchmarkRuns=5 -PbenchmarkOutputDir=build/reports/benchmark-validation -PbenchmarkReportPath=docs/deliverables/benchmark-validation-report.md`
- Iterations: 5
- Benchmark JVM command: `java --enable-preview --add-modules jdk.incubator.vector --class-path engines/build/classes/java/main:engines/build/classes/java/test:core/build/classes/java/main com.omaarr90.statecraft.engines.statevector.StatevectorKernelMicrobenchmark`
- Classpath: `engines/build/classes/java/main:engines/build/classes/java/test:core/build/classes/java/main`

## Benchmark Runs
| Run | AoS (ms) | Split (ms) | Speedup (x) | Max abs diff |
| --- | ---: | ---: | ---: | ---: |
| 1 | 1257.370 | 1467.985 | 1.170 | 0.000e+00 |
| 2 | 1261.139 | 1470.171 | 1.170 | 0.000e+00 |
| 3 | 1265.211 | 1477.075 | 1.170 | 0.000e+00 |
| 4 | 1269.414 | 1471.015 | 1.160 | 0.000e+00 |
| 5 | 1273.639 | 1473.049 | 1.160 | 0.000e+00 |

## Benchmark Summary
| Metric | Mean | Min | Max | Stddev |
| --- | ---: | ---: | ---: | ---: |
| AoS (ms) | 1265.355 | 1257.370 | 1273.639 | 5.773 |
| Split (ms) | 1471.859 | 1467.985 | 1477.075 | 3.072 |
| Speedup (x) | 1.166 | 1.160 | 1.170 | 0.005 |

- Worst max abs diff: 0.000e+00

## Validation Summary
- Validation task inputs: `./gradlew test --console=plain`
- Suites: 24
- Tests: 230
- Failures: 0
- Errors: 0
- Skipped: 0
- Total time (from JUnit XML): 0.658 s

## Validation by Module
| Module | Suites | Tests | Failures | Errors | Skipped | Time (s) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| app | 2 | 22 | 0 | 0 | 0 | 0.220 |
| core | 12 | 125 | 0 | 0 | 0 | 0.134 |
| engines | 10 | 83 | 0 | 0 | 0 | 0.304 |

## Validation by Suite
| Module | Suite | Tests | Failures | Errors | Skipped | Time (s) |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| app | com.omaarr90.statecraft.NoiseOptionsSupportTest | 11 | 0 | 0 | 0 | 0.135 |
| app | com.omaarr90.statecraft.StatecraftCliTest | 11 | 0 | 0 | 0 | 0.085 |
| core | com.omaarr90.statecraft.core.engine.MeasurementInstructionTest | 6 | 0 | 0 | 0 | 0.008 |
| core | com.omaarr90.statecraft.core.engine.MeasurementResultTest | 2 | 0 | 0 | 0 | 0.002 |
| core | com.omaarr90.statecraft.core.engine.SimulationRequestTest | 9 | 0 | 0 | 0 | 0.007 |
| core | com.omaarr90.statecraft.core.math.ComplexArraysTest | 11 | 0 | 0 | 0 | 0.004 |
| core | com.omaarr90.statecraft.core.math.ComplexArraysTinyOpsTest | 13 | 0 | 0 | 0 | 0.004 |
| core | com.omaarr90.statecraft.core.noise.ErrorChannelTest | 28 | 0 | 0 | 0 | 0.037 |
| core | com.omaarr90.statecraft.core.noise.NoiseModelTest | 15 | 0 | 0 | 0 | 0.005 |
| core | com.omaarr90.statecraft.core.parse.JsonCircuitParserTest | 5 | 0 | 0 | 0 | 0.051 |
| core | com.omaarr90.statecraft.core.parse.OpenQasmCircuitParserTest | 17 | 0 | 0 | 0 | 0.012 |
| core | com.omaarr90.statecraft.quantum.QuantumCircuitTest | 10 | 0 | 0 | 0 | 0.001 |
| core | com.omaarr90.statecraft.quantum.SingleQubitGateTest | 6 | 0 | 0 | 0 | 0.002 |
| core | com.omaarr90.statecraft.quantum.StateVectorTest | 3 | 0 | 0 | 0 | 0.001 |
| engines | com.omaarr90.statecraft.engines.CrossEngineConformanceTest | 5 | 0 | 0 | 0 | 0.066 |
| engines | com.omaarr90.statecraft.engines.SimulatorEnginesTest | 3 | 0 | 0 | 0 | 0.001 |
| engines | com.omaarr90.statecraft.engines.stabilizer.StabilizerEngineTest | 15 | 0 | 0 | 0 | 0.008 |
| engines | com.omaarr90.statecraft.engines.stabilizer.StabilizerRandomCliffordConformanceTest | 1 | 0 | 0 | 0 | 0.034 |
| engines | com.omaarr90.statecraft.engines.statevector.CircuitParsingIntegrationTest | 3 | 0 | 0 | 0 | 0.061 |
| engines | com.omaarr90.statecraft.engines.statevector.StatevectorAlgorithmSuiteTest | 4 | 0 | 0 | 0 | 0.001 |
| engines | com.omaarr90.statecraft.engines.statevector.StatevectorEngineTest | 23 | 0 | 0 | 0 | 0.109 |
| engines | com.omaarr90.statecraft.engines.statevector.StatevectorNoiseTest | 13 | 0 | 0 | 0 | 0.012 |
| engines | com.omaarr90.statecraft.engines.tensornetwork.MpsStateTest | 3 | 0 | 0 | 0 | 0.007 |
| engines | com.omaarr90.statecraft.engines.tensornetwork.TensorNetworkEngineTest | 13 | 0 | 0 | 0 | 0.005 |

## Reproducibility Commands
```bash
./gradlew benchmarkValidationReport --console=plain -PbenchmarkRuns=5 -PbenchmarkOutputDir=build/reports/benchmark-validation -PbenchmarkReportPath=docs/deliverables/benchmark-validation-report.md
```
