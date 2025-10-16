# Statevector Engine Follow-Up Plans

## Plan 1: Broaden Kernel Coverage (general 2-qubit + multi-control operations)

Goal: support arbitrary 2-qubit unitaries, controlled phase/rotation gates, and multi-controlled X so algorithms like QFT/GHZ run without engine changes.

Steps:
1. Survey existing `QuantumCircuit` operations to confirm required gate descriptors (e.g., SWAP, controlled-phase, Toffoli) and record any modelling gaps that block execution.
2. Define kernel entry points for:
   - generic 4×4 unitary application,
   - diagonal/phase-only two-qubit gates,
   - multi-controlled single-qubit rotations (including X/Toffoli) via bitmask iteration.
   Document expected preconditions (bit ordering, alignment).
3. Implement vectorized kernels using the JDK Vector API where profitable; fall back to scalar loops for tail or narrow strides. Reuse helper math (e.g., shared complex multiply routines) to avoid duplication.
4. Extend `QuantumCircuit` to emit differentiated operation types (SWAP, ControlledPhase, MultiControl) and update `StatevectorEngine` dispatch to wire them into the new kernels.
5. Add comprehensive tests:
   - unit tests covering the new kernels with randomly generated 4×4 unitaries,
   - circuit-level tests for GHZ, QFT (n ≤ 3), and Toffoli verification against reference `QuantumCircuit.apply`.
6. Benchmark on representative circuits to validate SIMD paths and check for regressions in existing single-qubit/CNOT performance.

## Plan 2: Measurement & Shots API

Goal: expose measurement sampling so callers can request shot-based histograms in addition to final amplitudes.

Steps:
1. Design API changes:
   - extend `SimulationRequest` with optional measurement instruction (shot count, measured qubits, RNG seed),
   - evolve `SimulationResult` to hold both `StateVector` (optional) and measurement outcomes (counts or raw samples).
   Capture proposals in ADR or design doc for review.
2. Update quantum model to represent measurement operations (e.g., new `QuantumCircuit.Operation.Measure` with classical register targets) and decide when collapse occurs.
3. Implement sampling routine:
   - derive probabilities from final state,
   - support partial-qubit measurement masks,
   - ensure deterministic behavior with provided seed.
   Optimize using alias tables or cumulative distributions for large shot counts.
4. Modify `StatevectorEngine.simulate` to respect measurement requests:
   - optionally skip returning full state when only shots are needed,
   - handle mid-circuit measurements if design demands (or document limits).
5. Extend CLI/demo and tests:
   - add integration tests asserting shot distributions converge to expected ratios,
   - update CLI to print counts when `--shots` is provided.
6. Document measurement semantics (collapse behavior, seeding, performance) in `docs/`.

## Plan 3: Align Kernels with AoS State Layout

Goal: eliminate real/imag split buffers so kernels operate directly on the project-standard interleaved `[re, im]` array.

Steps:
1. Evaluate current `StatevectorEngine` and `StateVector` interaction to map all read/write paths that expect split arrays. Identify helper utilities needed for AoS access.
2. Refactor `StatevectorKernels` signatures to accept a single `double[]` AoS buffer plus stride/offset metadata; implement vector loads/stores with gather/scatter when alignment permits.
3. Update `StatevectorEngine` to forward the underlying AoS data (reusing `StateVector.copyData`/`ComplexArrays` helpers) and eliminate intermediate copy buffers where safe.
4. Introduce utility methods for complex pair access (load/store) to keep kernel code readable and to centralize any alignment handling.
5. Adjust tests to validate no regressions and add targeted checks ensuring engine produces identical amplitudes before/after refactor. Include microbenchmarks that compare throughput with the previous layout.
6. Update documentation (ADR or README) to note the implementation now matches the declared AoS layout and record any alignment constraints or future enhancements.
