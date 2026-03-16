package com.omaarr90.statecraft.quantum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StateVectorTest {

	@Test
	void zeroRejectsQubitCountAboveDenseLimit() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> StateVector.zero(30));

		assertTrue(exception.getMessage().contains("at most 29 qubits"));
	}

	@Test
	void fromArrayRejectsQubitCountAboveDenseLimit() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> StateVector.fromArray(30, new double[0]));

		assertTrue(exception.getMessage().contains("at most 29 qubits"));
	}

	@Test
	void zeroAcceptsSupportedQubitCount() {
		StateVector state = StateVector.zero(5);
		assertEquals(5, state.qubitCount());
		assertEquals(32, state.dimension());
	}
}
