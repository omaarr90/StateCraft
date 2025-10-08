package com.omaarr90.statecraft.core.math;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class ComplexArraysTinyOpsTest {
    private static final double EPS = 1e-12;

    @Test
    void complexMath_mul_scalarCases() {
        ComplexNumber c1 = new ComplexNumber(1.0, 1.0).times(new ComplexNumber(1.0, -1.0)); // (1+i)*(1-i) = 2+0i
        assertEquals(2.0, c1.real(), EPS);
        assertEquals(0.0, c1.imag(), EPS);

        ComplexNumber c2 = new ComplexNumber(2.0, 3.0).times(new ComplexNumber(4.0, 5.0)); // (2+3i)*(4+5i) = -7 + 22i
        assertEquals(-7.0, c2.real(), EPS);
        assertEquals(22.0, c2.imag(), EPS);
    }

    @Test
    void scal_identityAndZeroAndRotate() {
        double[] a = {1,2, 3,4, 5,6};
        double[] orig = a.clone();

        // alpha = 1+0i leaves unchanged
        ComplexArrays.scal(a, 0, 3, 1, 0);
        assertArrayEquals(orig, a, EPS);

        // alpha = 0+0i zeroes
        ComplexArrays.scal(a, 0, 3, 0, 0);
        assertArrayEquals(new double[]{0,0, 0,0, 0,0}, a, EPS);

        // alpha = 0+1i rotates: (re,im)->(-im,re)
        double[] b = {1,2, 3,4, 5,6};
        ComplexArrays.scal(b, 0, 3, 0, 1);
        assertArrayEquals(new double[]{-2,1, -4,3, -6,5}, b, EPS);
    }

    @Test
    void axpy_alphaOne_matchesYPlusEqualX() {
        double[] x = {1,2, 3,4, 5,6};
        double[] y = {7,8, 9,10, 11,12};
        double[] expected = y.clone();
        for (int i=0;i<expected.length;i++) expected[i] += x[i];
        ComplexArrays.axpy(1, 0, x, 0, y, 0, 3);
        assertArrayEquals(expected, y, EPS);
    }

    @Test
    void mul_simplePatterns() {
        double[] x = {1,1, 2,3};
        double[] y = {1,-1, 4,5};
        double[] out = new double[4];
        ComplexArrays.mul(x, 0, y, 0, out, 0, 2);
        assertArrayEquals(new double[]{2,0, -7,22}, out, EPS);
    }

    @Test
    void axpy_alias_xEqualsY() {
        double[] y = {1,2, 3,4, 5,6};
        double[] orig = y.clone();
        // y <- alpha*y + y = (alpha+1)*y
        double ar = 0.5, ai = -1.0;
        ComplexArrays.axpy(ar, ai, y, 0, y, 0, 3);
        // compute expected (alpha+1) * orig
        double[] expected = orig.clone();
        for (int i=0;i<expected.length;i+=2) {
            double re = expected[i], im = expected[i+1];
            double rr = (ar+1)*re - ai*im;
            double ri = (ar+1)*im + ai*re;
            expected[i] = rr; expected[i+1] = ri;
        }
        assertArrayEquals(expected, y, EPS);
    }

    @Test
    void mul_inPlace_outEqualsX_or_outEqualsY() {
        double[] x = {2,3, 4,5};
        double[] y = {6,7, 8,9};

        double[] out1 = x.clone();
        ComplexArrays.mul(out1, 0, y, 0, out1, 0, 2); // out==x
        assertArrayEquals(new double[]{2*6-3*7, 2*7+3*6, 4*8-5*9, 4*9+5*8}, out1, EPS);

        double[] out2 = y.clone();
        ComplexArrays.mul(x, 0, out2, 0, out2, 0, 2); // out==y
        assertArrayEquals(new double[]{2*6-3*7, 2*7+3*6, 4*8-5*9, 4*9+5*8}, out2, EPS);
    }

    @Test
    void mul_inPlace_identicalSliceAllowed_allThreeAlias() {
        double[] a = {1,2, 3,4};
        double[] expected = a.clone();
        // square each element: a = a * a
        for (int i=0;i<expected.length;i+=2) {
            double re = expected[i], im = expected[i+1];
            double rr = re*re - im*im;
            double ri = re*im + im*re;
            expected[i] = rr; expected[i+1] = ri;
        }
        ComplexArrays.mul(a, 0, a, 0, a, 0, 2);
        assertArrayEquals(expected, a, EPS);
    }

    @Test
    void axpy_overlappingSlices_sameArray() {
        int nC = 4;
        double[] buf = new double[]{1,2, 3,4, 5,6, 7,8, 9,10, 11,12}; // 6 complex
        double[] bufOrig = buf.clone();
        int xOffC = 0;
        int yOffC = 2; // overlap
        double ar = 2, ai = -1;

        // compute expected on a pristine copy
        double[] expected = bufOrig.clone();
        for (int k=0;k<nC;k++) {
            int xi = (xOffC + k) * 2;
            int yi = (yOffC + k) * 2;
            double xr = bufOrig[xi];
            double xi_im = bufOrig[xi+1];
            double yr = bufOrig[yi];
            double yi_im = bufOrig[yi+1];
            double tr = ar * xr - ai * xi_im;
            double ti = ar * xi_im + ai * xr;
            expected[yi] = yr + tr;
            expected[yi+1] = yi_im + ti;
        }

        // run on original (overlapping)
        ComplexArrays.axpy(ar, ai, buf, xOffC, buf, yOffC, nC);
        assertArrayEquals(expected, buf, EPS);
    }

    @Test
    void offsets_middleSlice_guardsUntouched() {
        double[] a = {100,200, 1,2, 3,4, 5,6, 700,800}; // guards at ends
        double[] orig = a.clone();
        // operate on middle 3 complexes starting at offC=1
        ComplexArrays.scal(a, 1, 3, 0, 1); // rotate
        // verify guards untouched
        assertEquals(orig[0], a[0], EPS);
        assertEquals(orig[1], a[1], EPS);
        assertEquals(orig[8], a[8], EPS);
        assertEquals(orig[9], a[9], EPS);
    }

    @Test
    void exceptional_nullsAndSizesAndBounds() {
        // nulls
        assertThrows(NullPointerException.class, () -> ComplexArrays.scal(null, 0, 0, 1, 0));
        assertThrows(NullPointerException.class, () -> ComplexArrays.axpy(1, 0, null, 0, new double[2], 0, 1));
        assertThrows(NullPointerException.class, () -> ComplexArrays.axpy(1, 0, new double[2], 0, null, 0, 1));
        assertThrows(NullPointerException.class, () -> ComplexArrays.mul(null, 0, new double[2], 0, new double[2], 0, 1));
        assertThrows(NullPointerException.class, () -> ComplexArrays.mul(new double[2], 0, null, 0, new double[2], 0, 1));
        assertThrows(NullPointerException.class, () -> ComplexArrays.mul(new double[2], 0, new double[2], 0, null, 0, 1));

        // nC < 0
        assertThrows(IllegalArgumentException.class, () -> ComplexArrays.scal(new double[2], 0, -1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> ComplexArrays.axpy(1, 0, new double[2], 0, new double[2], 0, -1));
        assertThrows(IllegalArgumentException.class, () -> ComplexArrays.mul(new double[2], 0, new double[2], 0, new double[2], 0, -1));

        // out of bounds
        double[] a = new double[4];
        double[] b = new double[4];
        assertThrows(IndexOutOfBoundsException.class, () -> ComplexArrays.scal(a, 2, 2, 1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> ComplexArrays.axpy(1, 0, a, 0, b, 2, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> ComplexArrays.mul(a, 1, b, 0, new double[4], 0, 2));
    }

    @Test
    void mul_illegalAlias_outAliasesBothButDifferentSlices_throws() {
        double[] a = {1,2, 3,4, 5,6};
        // out==a, x==a, y==a but with different offsets so slices differ
        assertThrows(IllegalArgumentException.class, () ->
                ComplexArrays.mul(a, 0, a, 1, a, 2, 1));
    }

    @Test
    void nanAndInf_propagation() {
        // NaN in x propagates to y in axpy
        double[] x = {Double.NaN, 0.0};
        double[] y = {1.0, 2.0};
        ComplexArrays.axpy(1, 0, x, 0, y, 0, 1);
        assertTrue(Double.isNaN(y[0]) || Double.isNaN(y[1]));

        // Infinity in scal propagates
        double[] a = {Double.POSITIVE_INFINITY, 1.0};
        ComplexArrays.scal(a, 0, 1, 1, 0);
        assertTrue(Double.isInfinite(a[0]) || Double.isInfinite(a[1]));
    }

    @Test
    void randomized_smallTrials_againstNaive() {
        Random rnd = new Random(1234);
        for (int trial=0; trial<100; trial++) {
            int nC = 5;
            double[] x = new double[nC*2];
            double[] y = new double[nC*2];
            for (int i=0;i<x.length;i++) { x[i] = rnd.nextDouble()*2-1; y[i] = rnd.nextDouble()*2-1; }
            double ar = rnd.nextDouble()*2-1;
            double ai = rnd.nextDouble()*2-1;

            // axpy
            double[] yExpected = y.clone();
            for (int i=0;i<x.length;i+=2) {
                double xr = x[i], xi = x[i+1];
                double yr = yExpected[i], yi = yExpected[i+1];
                double tr = ar * xr - ai * xi;
                double ti = ar * xi + ai * xr;
                yExpected[i] = yr + tr;
                yExpected[i+1] = yi + ti;
            }
            ComplexArrays.axpy(ar, ai, x, 0, y, 0, nC);
            assertArrayEquals(yExpected, y, 1e-9);

            // mul out-of-place
            double[] out = new double[nC*2];
            for (int i=0;i<x.length;i+=2) {
                double xr = x[i], xi = x[i+1];
                double yr = y[i], yi = y[i+1];
                out[i] = xr*yr - xi*yi;
                out[i+1] = xr*yi + xi*yr;
            }
            double[] got = new double[nC*2];
            ComplexArrays.mul(x, 0, y, 0, got, 0, nC);
            assertArrayEquals(out, got, 1e-9);
        }
    }
}
