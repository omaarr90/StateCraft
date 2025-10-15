# ADR: Statevector Layout & Bit Order

**Status**: Accepted

**Date**: 2025-09-19

**Deciders**: Statecraft Core

**Context**: Phase 2 — core math kernel & circuit parser

## Decision

1. **Bit Order (Endianness)**

    * We adopt **little-endian qubit indexing**: qubit **q0** is the **least-significant bit (LSB)** in basis-state indices.
    * Integer index mapping:

      $$
      \text{index} = \sum_{k=0}^{n-1} b_k \cdot 2^k,\quad b_k \in \{0,1\} \text{ is the value of qubit } q_k
      $$
    * **Display convention**: when rendering bitstrings (CLI/logs/JSON counts), show **MSB on the left** (human-friendly): e.g., `"10"` for a 2-qubit state means $q_1{=}1, q_0{=}0$.

2. **Element Precision**

    * **Default**: double-precision complex (**64-bit real + 64-bit imag**).
    * Expandable later to single precision for memory/throughput trade-offs.

3. **In-Memory Complex Layout**

    * **Interleaved AoS (Array-of-Structs)**: amplitudes stored as `[re0, im0, re1, im1, …]` in one flat `double[]`.
    * Rationale: common industry/NumPy convention, good cache locality, simpler validation and BLAS-like kernels.

## Canonical Index Examples

* **2 qubits** $(q_1\,q_0)$:
  $|00⟩ \to 0,\ |01⟩ \to 1,\ |10⟩ \to 2,\ |11⟩ \to 3$.
* **3 qubits** $(q_2\,q_1\,q_0)$:
  $|b_2 b_1 b_0⟩ \to b_0 + 2 b_1 + 4 b_2$.
  Example: $|101⟩$ ($q_2{=}1,q_1{=}0,q_0{=}1$) $\to 5$.

## Consequences

* **Pros**

    * Aligns with mainstream quantum tooling and NumPy’s `complex128`.
    * Simplifies SIMD-friendly fused kernels (complex mul/add on interleaved pairs).
    * Straightforward cross-validation against external simulators.

* **Cons**

    * Some SIMD strategies prefer **SoA (planar)** `[r…][i…]`; adopting AoS may require shuffles for certain vector widths.
    * MSB-left display can confuse newcomers vs LSB-indexed memory; documentation and examples mitigate this.

## Alternatives Considered

* **Planar SoA**: separate real/imag arrays. Better for some vectorization patterns, but diverges from common ecosystems and complicates interop.
* **Big-endian indexing**: makes printed strings match memory order visually, but clashes with widespread little-endian assumptions and code samples.

## Implementation Notes

* The state vector is a contiguous array of length $2^n$ complex numbers ⇒ **$2^{n+1}$** doubles total.
* Recommend 16-byte alignment minimum; prefer 32-/64-byte for AVX/AVX-512 when allocating large arrays.
* SIMD kernels (`StatevectorKernels`) operate directly on this AoS layout; gather/scatter shuffles stay localized so higher layers no longer allocate planar scratch buffers.
* CLI/JSON should clearly state that printed bitstrings are MSB-left to avoid confusion when comparing with indices.

## Validation

* Golden tests: prepare small $n$ states with known amplitudes; verify index↔basis mapping and numeric equality after round-trips.
* Cross-check counts/bitstrings against a reference simulator for simple circuits (X, CNOT, Bell state) to confirm endianness and display conventions.

## Future Work (Non-Blocking)

* Build-time or runtime switch for single precision.
* Optional SoA scratch buffers inside hot kernels if profiling shows wins.
