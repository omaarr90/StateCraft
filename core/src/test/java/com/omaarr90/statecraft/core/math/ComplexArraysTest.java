package com.omaarr90.statecraft.core.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ComplexArraysTest {

    private static final double EPS = 1e-12;

    @Test
    void norm2_singleComplex_345Triangle() {
        ComplexArrays ca = new ComplexArrays();
        double[] a = {3.0, 4.0}; // |3+4i| = 5
        assertEquals(25.0, ca.norm2Sq(a), EPS);
        assertEquals(5.0, ca.norm2(a), EPS);
    }

    @Test
    void norm2_multipleValues_totalEnergyAndMagnitude() {
        ComplexArrays ca = new ComplexArrays();
        double[] a = {1.0, 2.0, 3.0, 4.0}; // (1^2+2^2) + (3^2+4^2) = 5 + 25 = 30
        double norm2Sq = ca.norm2Sq(a);
        assertEquals(30.0, norm2Sq, EPS);
        assertEquals(Math.sqrt(30.0), ca.norm2(a), EPS);
    }

    @Test
    void norm2_slice_secondElementOnly() {
        ComplexArrays ca = new ComplexArrays();
        double[] a = {9.0, 12.0, 3.0, 4.0}; // |9+12i|^2 = 225; |3+4i|^2 = 25
        // Slice to select only the second complex (offset=2 doubles, count=1 complex)
        assertEquals(25.0, ca.norm2Sq(a, 2, 1), EPS);
        assertEquals(5.0, ca.norm2(a, 2, 1), EPS);
    }

    @Test
    void norm2_zeroLengthRanges_areZero() {
        ComplexArrays ca = new ComplexArrays();
        double[] a = {1.0, 2.0, 3.0, 4.0};
        assertEquals(0.0, ca.norm2Sq(a, 0, 0), EPS);
        assertEquals(0.0, ca.norm2(a, 0, 0), EPS);
        // offset at end with zero count
        assertEquals(0.0, ca.norm2Sq(a, a.length, 0), EPS);
        assertEquals(0.0, ca.norm2(a, a.length, 0), EPS);
    }

    @Test
    void nullArray_throwsNPE() {
        ComplexArrays ca = new ComplexArrays();
        assertThrows(NullPointerException.class, () -> ca.norm2Sq(null));
        assertThrows(NullPointerException.class, () -> ca.norm2(null));
        assertThrows(NullPointerException.class, () -> ca.norm2Sq(null, 0, 0));
        assertThrows(NullPointerException.class, () -> ca.norm2(null, 0, 0));
    }

    @Test
    void negativeOffsetOrCount_throwsIAE() {
        ComplexArrays ca = new ComplexArrays();
        double[] a = {1.0, 2.0};
        assertThrows(IllegalArgumentException.class, () -> ca.norm2Sq(a, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> ca.norm2Sq(a, 0, -2));
    }

    @Test
    void outOfBounds_throwsIAE() {
        ComplexArrays ca = new ComplexArrays();
        double[] a = {1.0, 2.0, 3.0, 4.0};
        assertThrows(IllegalArgumentException.class, () -> ca.norm2Sq(a, 0, 3)); // count too large (needs 6 doubles)
        assertThrows(IllegalArgumentException.class, () -> ca.norm2Sq(a, 4, 2)); // offset==length then count>0
        assertThrows(IllegalArgumentException.class, () -> ca.norm2Sq(a, 2, 2)); // end beyond length
    }

    @Test
    void oddOffset_throwsIAE_countMayBeOdd() {
        ComplexArrays ca = new ComplexArrays();
        double[] a = {1.0, 2.0, 3.0, 4.0};
        assertThrows(IllegalArgumentException.class, () -> ca.norm2Sq(a, 1, 2)); // odd offset
        assertDoesNotThrow(() -> ca.norm2Sq(a, 0, 1)); // odd complexCount is valid
        assertThrows(IllegalArgumentException.class, () -> ca.norm2Sq(a, 1, 1)); // odd offset still invalid
    }

    @Test
    void oddLengthArray_convenienceOverloadsThrow() {
        ComplexArrays ca = new ComplexArrays();
        double[] odd = {1.0};
        assertThrows(IllegalArgumentException.class, () -> ca.norm2Sq(odd));
        assertThrows(IllegalArgumentException.class, () -> ca.norm2(odd));
    }

    @Test
    void nanComponents_propagateToNaN() {
        ComplexArrays ca = new ComplexArrays();
        double[] a = {Double.NaN, 0.0};
        assertTrue(Double.isNaN(ca.norm2Sq(a)));
        assertTrue(Double.isNaN(ca.norm2(a)));

        double[] b = {1.0, Double.NaN, 3.0, 4.0};
        assertTrue(Double.isNaN(ca.norm2Sq(b)));
        assertTrue(Double.isNaN(ca.norm2(b)));
    }

    @Test
    void infinities_propagateToInfinity() {
        ComplexArrays ca = new ComplexArrays();
        double[] a = {Double.POSITIVE_INFINITY, 0.0};
        assertTrue(Double.isInfinite(ca.norm2Sq(a)));
        assertTrue(Double.isInfinite(ca.norm2(a)));
    }
}
