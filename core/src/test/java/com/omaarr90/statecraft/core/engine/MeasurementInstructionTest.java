package com.omaarr90.statecraft.core.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class MeasurementInstructionTest {

	@Test
	void countsAllUsesHistogramMode() {
		MeasurementInstruction instruction = MeasurementInstruction.countsAll(128);
		assertEquals(128, instruction.shots());
		assertTrue(instruction.measuredQubits().isEmpty());
		assertEquals(MeasurementInstruction.Mode.COUNTS, instruction.mode());
		assertTrue(instruction.seed().isEmpty());
	}

	@Test
	void samplesAllUsesSampleMode() {
		MeasurementInstruction instruction = MeasurementInstruction.samplesAll(64).withSeed(11L);
		assertEquals(MeasurementInstruction.Mode.SAMPLES, instruction.mode());
		assertTrue(instruction.measuredQubits().isEmpty());
		assertTrue(instruction.seed().isPresent());
		assertEquals(11L, instruction.seed().getAsLong());
	}

	@Test
	void countsNormalizesQubits() {
		MeasurementInstruction instruction = MeasurementInstruction.counts(4, 3, 1, 2);
		int[] qubits = instruction.measuredQubits().orElseThrow();
		assertArrayEquals(new int[]{1, 2, 3}, qubits);
	}

	@Test
	void duplicateMeasuredQubitsThrow() {
		assertThrows(IllegalArgumentException.class, () -> MeasurementInstruction.counts(1, 0, 0));
	}

	@Test
	void negativeMeasuredQubitThrows() {
		assertThrows(IllegalArgumentException.class, () -> new MeasurementInstruction(8, Optional.of(new int[]{-1}),
				OptionalLong.empty(), MeasurementInstruction.Mode.COUNTS));
	}

	@Test
	void withSeedProducesIndependentInstance() {
		MeasurementInstruction instruction = MeasurementInstruction.counts(8, 0, 2);
		MeasurementInstruction seeded = instruction.withSeed(42L);
		assertTrue(seeded.seed().isPresent());
		assertEquals(42L, seeded.seed().getAsLong());
		assertArrayEquals(new int[]{0, 2}, seeded.measuredQubits().orElseThrow());
		assertTrue(instruction.seed().isEmpty());
	}
}
