package com.omaarr90.statecraft.core.noise;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omaarr90.statecraft.core.math.ComplexNumber;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErrorChannelTest {

	private static final double EPS = 1e-12;

	@Test
	void depolarizingChannelValidatesCompleteness() {
		ErrorChannel channel = ErrorChannel.depolarizing(0.1, 0);
		KrausDecomposition decomp = channel.krausDecomposition();
		decomp.validateCompleteness();
	}

	@Test
	void depolarizingChannelRejectsBadProbability() {
		assertThrows(IllegalArgumentException.class, () -> ErrorChannel.depolarizing(-0.1, 0));
		assertThrows(IllegalArgumentException.class, () -> ErrorChannel.depolarizing(1.5, 0));
	}

	@Test
	void depolarizingChannelRejectsMultipleQubits() {
		assertThrows(IllegalArgumentException.class, () -> ErrorChannel.depolarizing(0.1, 0, 1));
	}

	@Test
	void amplitudeDampingChannelValidatesCompleteness() {
		ErrorChannel channel = ErrorChannel.amplitudeDamping(0.3, 0);
		KrausDecomposition decomp = channel.krausDecomposition();
		decomp.validateCompleteness();
	}

	@Test
	void amplitudeDampingChannelRejectsBadGamma() {
		assertThrows(IllegalArgumentException.class, () -> ErrorChannel.amplitudeDamping(-0.1, 0));
		assertThrows(IllegalArgumentException.class, () -> ErrorChannel.amplitudeDamping(1.5, 0));
	}

	@Test
	void phaseFlipChannelValidatesCompleteness() {
		ErrorChannel channel = ErrorChannel.phaseFlip(0.2, 0);
		KrausDecomposition decomp = channel.krausDecomposition();
		decomp.validateCompleteness();
	}

	@Test
	void phaseDampingChannelValidatesCompleteness() {
		ErrorChannel channel = ErrorChannel.phaseDamping(0.15, 0);
		KrausDecomposition decomp = channel.krausDecomposition();
		decomp.validateCompleteness();
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({"ordinary gate, 5.0e-5, 3.0e-5, 1.0e-7", "zero gate, 5.0e-5, 3.0e-5, 0.0",
			"T2 equals two T1, 5.0e-5, 1.0e-4, 1.0e-7", "strong dephasing, 5.0e-5, 1.0e-6, 1.0e-7",
			"very small gate, 5.0e-5, 3.0e-5, 1.0e-15", "large gate, 5.0e-5, 3.0e-5, 5.0e-3"})
	void thermalRelaxationChannelValidatesCompleteness(String label, double t1, double t2, double gateTime) {
		ErrorChannel channel = ErrorChannel.thermalRelaxation(t1, t2, gateTime, 0);
		KrausDecomposition decomp = channel.krausDecomposition();
		assertFiniteKrausEntries(decomp);
		decomp.validateCompleteness();
	}

	@Test
	void thermalRelaxationWithZeroGateTimeBehavesAsIdentity() {
		ErrorChannel channel = ErrorChannel.thermalRelaxation(50e-6, 30e-6, 0.0, 0);
		KrausDecomposition decomp = channel.krausDecomposition();
		decomp.validateCompleteness();

		ComplexNumber[] plusDensity = {new ComplexNumber(0.5, 0.0), new ComplexNumber(0.5, 0.0),
				new ComplexNumber(0.5, 0.0), new ComplexNumber(0.5, 0.0)};
		ComplexNumber[] evolved = applyChannel(decomp, plusDensity);
		assertMatrixEquals(plusDensity, evolved);
	}

	@Test
	void thermalRelaxationWithLargeGateTimeDampsAndDecoheres() {
		ErrorChannel channel = ErrorChannel.thermalRelaxation(50e-6, 30e-6, 5e-3, 0);
		KrausDecomposition decomp = channel.krausDecomposition();
		decomp.validateCompleteness();

		ComplexNumber[] oneDensity = {ComplexNumber.zero(), ComplexNumber.zero(), ComplexNumber.zero(),
				ComplexNumber.one()};
		ComplexNumber[] evolvedOne = applyChannel(decomp, oneDensity);
		assertTrue(evolvedOne[0].real() > 1.0 - 1e-8);
		assertTrue(Math.abs(evolvedOne[3].real()) < 1e-8);

		ComplexNumber[] plusDensity = {new ComplexNumber(0.5, 0.0), new ComplexNumber(0.5, 0.0),
				new ComplexNumber(0.5, 0.0), new ComplexNumber(0.5, 0.0)};
		ComplexNumber[] evolvedPlus = applyChannel(decomp, plusDensity);
		assertTrue(Math.abs(evolvedPlus[1].real()) < 1e-8);
		assertTrue(Math.abs(evolvedPlus[1].imag()) < 1e-8);
	}

	@Test
	void thermalRelaxationIncludesNonZeroDecayOperator() {
		ErrorChannel channel = ErrorChannel.thermalRelaxation(50e-6, 30e-6, 5e-3, 0);
		KrausDecomposition decomposition = channel.krausDecomposition();
		assertTrue(decomposition.operators().get(2).matrix()[1].magnitudeSquared() > 0.1);
	}

	@Test
	void thermalRelaxationChannelRejectsInvalidT1() {
		assertThrows(IllegalArgumentException.class, () -> ErrorChannel.thermalRelaxation(-1.0, 1.0, 0.1, 0));
		assertThrows(IllegalArgumentException.class, () -> ErrorChannel.thermalRelaxation(Double.NaN, 1.0, 0.1, 0));
		assertThrows(IllegalArgumentException.class,
				() -> ErrorChannel.thermalRelaxation(Double.POSITIVE_INFINITY, 1.0, 0.1, 0));
	}

	@Test
	void thermalRelaxationChannelRejectsInvalidT2() {
		assertThrows(IllegalArgumentException.class, () -> ErrorChannel.thermalRelaxation(1.0, -1.0, 0.1, 0));
		assertThrows(IllegalArgumentException.class, () -> ErrorChannel.thermalRelaxation(1.0, Double.NaN, 0.1, 0));
		assertThrows(IllegalArgumentException.class,
				() -> ErrorChannel.thermalRelaxation(1.0, Double.POSITIVE_INFINITY, 0.1, 0));
		assertThrows(IllegalArgumentException.class, () -> ErrorChannel.thermalRelaxation(1.0, 3.0, 0.1, 0));
	}

	@Test
	void thermalRelaxationChannelRejectsNegativeGateTime() {
		assertThrows(IllegalArgumentException.class, () -> ErrorChannel.thermalRelaxation(1.0, 0.5, -0.1, 0));
		assertThrows(IllegalArgumentException.class, () -> ErrorChannel.thermalRelaxation(1.0, 0.5, Double.NaN, 0));
		assertThrows(IllegalArgumentException.class,
				() -> ErrorChannel.thermalRelaxation(1.0, 0.5, Double.POSITIVE_INFINITY, 0));
	}

	@Test
	void krausDecompositionAllowsStateDependentSamplingOperators() {
		KrausOperator identity = ErrorChannel.singleQubitOperator(ComplexNumber.one(), ComplexNumber.zero(),
				ComplexNumber.zero(), ComplexNumber.one());
		KrausOperator zero = ErrorChannel.singleQubitOperator(ComplexNumber.zero(), ComplexNumber.zero(),
				ComplexNumber.zero(), ComplexNumber.zero());

		KrausDecomposition decomposition = assertDoesNotThrow(() -> new KrausDecomposition(List.of(identity, zero), 1));
		assertDoesNotThrow(decomposition::validateCompleteness);
	}

	@Test
	void affectedQubitsCopied() {
		ErrorChannel channel = ErrorChannel.depolarizing(0.1, 5);
		int[] qubits = channel.affectedQubits();
		assertEquals(5, qubits[0]);

		qubits[0] = 999;
		int[] qubits2 = channel.affectedQubits();
		assertEquals(5, qubits2[0]);
	}

	@Test
	void compositeChannelCombinesAffectedQubits() {
		ErrorChannel ch1 = ErrorChannel.depolarizing(0.1, 0);
		ErrorChannel ch2 = ErrorChannel.phaseFlip(0.05, 1);
		ErrorChannel composite = ErrorChannel.compose(ch1, ch2);

		int[] qubits = composite.affectedQubits();
		assertArrayEquals(new int[]{0, 1}, qubits);
	}

	@Test
	void compositeChannelRejectsEmpty() {
		assertThrows(IllegalArgumentException.class, () -> ErrorChannel.compose());
	}

	@Test
	void compositeChannelReturnsComponents() {
		ErrorChannel ch1 = ErrorChannel.depolarizing(0.1, 0);
		ErrorChannel ch2 = ErrorChannel.phaseFlip(0.05, 0);
		CompositeChannel composite = (CompositeChannel) ErrorChannel.compose(ch1, ch2);

		ErrorChannel[] components = composite.getChannels();
		assertEquals(2, components.length);
		assertEquals(ch1, components[0]);
		assertEquals(ch2, components[1]);
	}

	@Test
	void krausDecompositionRejectsEmptyOperators() {
		assertThrows(IllegalArgumentException.class, () -> new KrausDecomposition(java.util.List.of(), 1));
	}

	@Test
	void krausDecompositionRejectsInconsistentDimensions() {
		KrausOperator op1 = ErrorChannel.singleQubitOperator(ComplexNumber.one(), ComplexNumber.zero(),
				ComplexNumber.zero(), ComplexNumber.one());
		ComplexNumber[] matrix2 = new ComplexNumber[16];
		for (int i = 0; i < 16; i++) {
			matrix2[i] = ComplexNumber.zero();
		}
		KrausOperator op2 = new KrausOperator(matrix2);

		assertThrows(IllegalArgumentException.class, () -> new KrausDecomposition(java.util.List.of(op1, op2), 1));
	}

	@Test
	void krausOperatorComputesDimension() {
		KrausOperator op = ErrorChannel.singleQubitOperator(ComplexNumber.one(), ComplexNumber.zero(),
				ComplexNumber.zero(), ComplexNumber.one());
		assertEquals(2, op.dimension());
		assertEquals(1, op.numQubits());
	}

	@Test
	void krausOperatorRejectsNoInsquareMatrix() {
		ComplexNumber[] matrix = new ComplexNumber[5];
		for (int i = 0; i < 5; i++) {
			matrix[i] = ComplexNumber.zero();
		}
		assertThrows(IllegalArgumentException.class, () -> new KrausOperator(matrix));
	}

	private static ComplexNumber[] applyChannel(KrausDecomposition decomposition, ComplexNumber[] density) {
		int dimension = 1 << decomposition.numQubits();
		ComplexNumber[] sum = new ComplexNumber[density.length];
		for (int idx = 0; idx < sum.length; idx++) {
			sum[idx] = ComplexNumber.zero();
		}
		for (KrausOperator operator : decomposition.operators()) {
			ComplexNumber[] k = operator.matrix();
			ComplexNumber[] kRho = multiply(k, density, dimension);
			ComplexNumber[] kDagger = conjugateTranspose(k, dimension);
			ComplexNumber[] contribution = multiply(kRho, kDagger, dimension);
			for (int idx = 0; idx < sum.length; idx++) {
				sum[idx] = sum[idx].plus(contribution[idx]);
			}
		}
		return sum;
	}

	private static void assertFiniteKrausEntries(KrausDecomposition decomposition) {
		for (KrausOperator operator : decomposition.operators()) {
			for (ComplexNumber entry : operator.matrix()) {
				assertTrue(Double.isFinite(entry.real()), "Kraus entry has non-finite real part: " + entry);
				assertTrue(Double.isFinite(entry.imag()), "Kraus entry has non-finite imaginary part: " + entry);
			}
		}
	}

	private static ComplexNumber[] conjugateTranspose(ComplexNumber[] matrix, int dimension) {
		ComplexNumber[] result = new ComplexNumber[matrix.length];
		for (int row = 0; row < dimension; row++) {
			for (int col = 0; col < dimension; col++) {
				result[col * dimension + row] = matrix[row * dimension + col].conjugate();
			}
		}
		return result;
	}

	private static ComplexNumber[] multiply(ComplexNumber[] left, ComplexNumber[] right, int dimension) {
		ComplexNumber[] result = new ComplexNumber[dimension * dimension];
		for (int row = 0; row < dimension; row++) {
			for (int col = 0; col < dimension; col++) {
				ComplexNumber total = ComplexNumber.zero();
				for (int mid = 0; mid < dimension; mid++) {
					total = total.plus(left[row * dimension + mid].times(right[mid * dimension + col]));
				}
				result[row * dimension + col] = total;
			}
		}
		return result;
	}

	private static void assertMatrixEquals(ComplexNumber[] expected, ComplexNumber[] actual) {
		assertEquals(expected.length, actual.length);
		for (int idx = 0; idx < expected.length; idx++) {
			assertEquals(expected[idx].real(), actual[idx].real(), EPS, "real mismatch at " + idx);
			assertEquals(expected[idx].imag(), actual[idx].imag(), EPS, "imag mismatch at " + idx);
		}
	}
}
