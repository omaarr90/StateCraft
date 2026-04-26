package com.omaarr90.statecraft.core.noise;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.List;
import java.util.Objects;

/**
 * Thermal relaxation channel combining T1 energy relaxation and T2 dephasing.
 * <p>
 * Models realistic qubit decoherence with a CPTP single-qubit Kraus
 * decomposition parameterized by gate time.
 * <p>
 * Constraint: T2 <= 2*T1 (physically required for positive rate)
 */
final class ThermalRelaxationChannel implements ErrorChannel {

	private static final double BOUNDED_FACTOR_TOLERANCE = 1e-12;
	private static final double RATE_TOLERANCE = 1e-12;

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
					"T2 must satisfy T2 <= 2*T1 (physical constraint), got T1=" + t1 + ", T2=" + t2);
		}
		if (gateTime < 0.0 || !Double.isFinite(gateTime)) {
			throw new IllegalArgumentException("gate time must be non-negative and finite, got " + gateTime);
		}
		if (qubits.length != 1) {
			throw new IllegalArgumentException("thermal relaxation channel currently only supports single qubits");
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
		double relaxationSurvival;
		double pureDephasingFactor;
		if (gateTime == 0.0) {
			relaxationSurvival = 1.0;
			pureDephasingFactor = 1.0;
		} else {
			relaxationSurvival = boundedFactor("relaxation survival", Math.exp(-gateTime / t1));
			double inverseT2 = 1.0 / t2;
			double halfInverseT1 = 1.0 / (2.0 * t1);
			double pureDephasingRate = nonNegativeRate("pure dephasing rate", inverseT2 - halfInverseT1,
					Math.abs(inverseT2) + Math.abs(halfInverseT1));
			pureDephasingFactor = boundedFactor("pure dephasing factor", Math.exp(-gateTime * pureDephasingRate));
		}
		double decayProbability = boundedFactor("decay probability", 1.0 - relaxationSurvival);
		double sqrtSurvival = Math.sqrt(relaxationSurvival);

		double plus = boundedFactor("thermal plus factor", 0.5 * (1.0 + pureDephasingFactor));
		double minus = boundedFactor("thermal minus factor", 0.5 * (1.0 - pureDephasingFactor));

		double k0Diag0 = Math.sqrt(plus);
		double k0Diag1 = sqrtSurvival * k0Diag0;
		ComplexNumber[] k0Matrix = {new ComplexNumber(k0Diag0, 0.0), ComplexNumber.zero(), ComplexNumber.zero(),
				new ComplexNumber(k0Diag1, 0.0)};

		double k1Diag0 = Math.sqrt(minus);
		double k1Diag1 = sqrtSurvival * k1Diag0;
		ComplexNumber[] k1Matrix = {new ComplexNumber(k1Diag0, 0.0), ComplexNumber.zero(), ComplexNumber.zero(),
				new ComplexNumber(-k1Diag1, 0.0)};

		double k2OffDiag = Math.sqrt(decayProbability);
		ComplexNumber[] k2Matrix = {ComplexNumber.zero(), new ComplexNumber(k2OffDiag, 0.0), ComplexNumber.zero(),
				ComplexNumber.zero()};

		KrausOperator k0 = new KrausOperator(k0Matrix);
		KrausOperator k1 = new KrausOperator(k1Matrix);
		KrausOperator k2 = new KrausOperator(k2Matrix);
		return new KrausDecomposition(List.of(k0, k1, k2), 1);
	}

	private static double nonNegativeRate(String name, double value, double scale) {
		if (!Double.isFinite(value)) {
			throw new IllegalStateException(name + " must be finite, got " + value);
		}
		if (value < 0.0) {
			double tolerance = RATE_TOLERANCE * Math.max(1.0, scale);
			if (value >= -tolerance) {
				return 0.0;
			}
			throw new IllegalStateException(name + " must be non-negative, got " + value);
		}
		return value;
	}

	private static double boundedFactor(String name, double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalStateException(name + " must be finite, got " + value);
		}
		if (value < 0.0) {
			if (value >= -BOUNDED_FACTOR_TOLERANCE) {
				return 0.0;
			}
			throw new IllegalStateException(name + " must be in [0,1], got " + value);
		}
		if (value > 1.0) {
			if (value <= 1.0 + BOUNDED_FACTOR_TOLERANCE) {
				return 1.0;
			}
			throw new IllegalStateException(name + " must be in [0,1], got " + value);
		}
		return value;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof ThermalRelaxationChannel other))
			return false;
		return Double.compare(t1, other.t1) == 0 && Double.compare(t2, other.t2) == 0
				&& Double.compare(gateTime, other.gateTime) == 0 && java.util.Arrays.equals(qubits, other.qubits);
	}

	@Override
	public int hashCode() {
		return Objects.hash(t1, t2, gateTime, java.util.Arrays.hashCode(qubits));
	}

	@Override
	public String toString() {
		return "ThermalRelaxationChannel[T1=" + t1 + ", T2=" + t2 + ", gateTime=" + gateTime + ", qubits="
				+ java.util.Arrays.toString(qubits) + "]";
	}
}
