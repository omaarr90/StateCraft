package com.omaarr90.statecraft.core.noise;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.List;
import java.util.Objects;

/**
 * Thermal relaxation channel combining T₁ energy relaxation and T₂ dephasing.
 * <p>
 * Models realistic qubit decoherence by combining amplitude damping (T₁)
 * and pure dephasing. The effective noise depends on the gate time.
 * <p>
 * The channel is constructed from:
 * <ul>
 *   <li>Amplitude damping with γ = 1 - exp(-gate_time / T₁)</li>
 *   <li>Phase damping with λ = (1 - exp(-gate_time / T₂)) - γ/2</li>
 * </ul>
 * <p>
 * Constraint: T₂ ≤ 2·T₁ (physically required for positive rate)
 */
final class ThermalRelaxationChannel implements ErrorChannel {

    private final double t1;
    private final double t2;
    private final double gateTime;
    private final int[] qubits;
    private final KrausDecomposition decomposition;

    ThermalRelaxationChannel(double t1, double t2, double gateTime, int... qubits) {
        if (t1 <= 0.0 || !Double.isFinite(t1)) {
            throw new IllegalArgumentException("T1 must be positive and finite, got " + t1);
        }
        if (t2 <= 0.0 || !Double.isFinite(t2)) {
            throw new IllegalArgumentException("T2 must be positive and finite, got " + t2);
        }
        if (t2 > 2.0 * t1) {
            throw new IllegalArgumentException(
                    "T2 must satisfy T2 ≤ 2·T1 (physical constraint), got T1=" + t1 + ", T2=" + t2);
        }
        if (gateTime < 0.0 || !Double.isFinite(gateTime)) {
            throw new IllegalArgumentException("gate time must be non-negative and finite, got "
                    + gateTime);
        }
        if (qubits.length != 1) {
            throw new IllegalArgumentException(
                    "thermal relaxation channel currently only supports single qubits");
        }

        this.t1 = t1;
        this.t2 = t2;
        this.gateTime = gateTime;
        this.qubits = qubits.clone();
        this.decomposition = buildKrausDecomposition();
    }

    @Override
    public KrausDecomposition krausDecomposition() {
        return decomposition;
    }

    @Override
    public int[] affectedQubits() {
        return qubits.clone();
    }

    private KrausDecomposition buildKrausDecomposition() {
        // Compute decay probabilities
        // p_a = 1 - exp(-t/T1): amplitude damping probability
        double pa = 1.0 - Math.exp(-gateTime / t1);
        
        // Pure dephasing time: 1/T2 = 1/(2*T1) + 1/T_phi
        // Therefore: T_phi = (T1 * T2) / (2*T1 - T2)
        double tPhi;
        if (Math.abs(2.0 * t1 - t2) < 1e-14) {
            // T2 = 2*T1 exactly, no pure dephasing
            tPhi = Double.POSITIVE_INFINITY;
        } else {
            tPhi = (t1 * t2) / (2.0 * t1 - t2);
        }
        
        // p_p = 1 - exp(-t/T_phi): pure phase damping probability
        double pp = tPhi == Double.POSITIVE_INFINITY ? 0.0 : (1.0 - Math.exp(-gateTime / tPhi));
        
        // Ensure non-negative due to numerical precision
        pp = Math.max(0.0, pp);

        // Monte Carlo probabilities for sampling
        // These are the trace values Tr(K_i† K_i)
        double p0 = (1.0 - pa) * (1.0 - pp);  // no error
        double p1 = pa;                        // amplitude damping
        double p2 = (1.0 - pa) * pp;          // pure dephasing

        // Kraus operator coefficients (square roots of probabilities)
        double c0 = Math.sqrt(p0);
        double c1 = Math.sqrt(p1);
        double c2 = Math.sqrt(p2);

        // K₀: no error (scaled identity)
        // K0 = sqrt(p0) * I
        KrausOperator k0 = ErrorChannel.singleQubitOperator(
                p0,
                new ComplexNumber(c0, 0.0),
                ComplexNumber.zero(),
                ComplexNumber.zero(),
                new ComplexNumber(c0, 0.0));

        // K₁: amplitude damping (|1⟩ → |0⟩)
        // K1 = [[0, sqrt(pa)], [0, 0]]
        KrausOperator k1 = ErrorChannel.singleQubitOperator(
                p1,
                ComplexNumber.zero(),
                new ComplexNumber(c1, 0.0),
                ComplexNumber.zero(),
                ComplexNumber.zero());

        // K₂: pure dephasing
        // K2 = sqrt(pp * (1-pa)) * Z = c2 * [[1, 0], [0, -1]]
        KrausOperator k2 = ErrorChannel.singleQubitOperator(
                p2,
                new ComplexNumber(c2, 0.0),
                ComplexNumber.zero(),
                ComplexNumber.zero(),
                new ComplexNumber(-c2, 0.0));

        return new KrausDecomposition(List.of(k0, k1, k2), 1);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof ThermalRelaxationChannel other)) return false;
        return Double.compare(t1, other.t1) == 0
                && Double.compare(t2, other.t2) == 0
                && Double.compare(gateTime, other.gateTime) == 0
                && java.util.Arrays.equals(qubits, other.qubits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(t1, t2, gateTime, java.util.Arrays.hashCode(qubits));
    }

    @Override
    public String toString() {
        return "ThermalRelaxationChannel[T1=" + t1 + ", T2=" + t2 + ", gateTime=" + gateTime
                + ", qubits=" + java.util.Arrays.toString(qubits) + "]";
    }
}
