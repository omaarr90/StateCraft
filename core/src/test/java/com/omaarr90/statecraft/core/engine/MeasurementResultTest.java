package com.omaarr90.statecraft.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MeasurementResultTest {

    @Test
    void bitstringHistogramStoresCounts() {
        MeasurementResult.BitstringHistogram histogram = new MeasurementResult.BitstringHistogram(
                new int[] {0, 32, 63},
                3,
                Map.of("000", 1, "101", 2));

        assertEquals(3, histogram.shots());
        assertEquals(2, histogram.counts().get("101"));
    }

    @Test
    void bitstringSamplesValidateWidth() {
        assertThrows(IllegalArgumentException.class, () -> new MeasurementResult.BitstringSamples(
                new int[] {0, 1},
                1,
                List.of("101")));
    }
}
