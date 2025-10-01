package com.omaarr90.statecraft.core.math;

/// Utilities for operations on interleaved complex arrays.
/// 
/// Layout: Array-of-Structs (AoS) — \[re0, im0, re1, im1, …\]. Each complex value occupies two consecutive doubles.
public class ComplexArrays {

    /** Converts an offset in complex units to the double index. */
    private static int toDoubleIndex(int offC) { return offC << 1; }

    /// Computes the Euclidean L2 norm of all complex values in the given array.
/// 
/// Assumes the array is in AoS layout \[re0, im0, re1, im1, …\]. This is a convenience overload
/// equivalent to norm2(a, 0, a.length/2).
///
/// Preconditions:
/// - a != null
/// - a.length must be even (pairs of re,im).
///
/// Numerical behavior:
/// - If any component is NaN, the result is NaN.
/// - If any component is infinite and none are NaN, the result is +Infinity.
    public static double norm2(double[] a) {
        if (a == null) throw new NullPointerException("array is null");
        if ((a.length & 1) != 0) {
            throw new IllegalArgumentException("array length must be even (re,im pairs)");
        }
        return norm2(a, 0, a.length / 2);
    }

    /// Computes the Euclidean L2 norm of count complex values starting at offset (double index).
/// 
/// Parameters:
/// - a: AoS array \[re0, im0, re1, im1, …]\
/// - offset: index in doubles where the first real component starts; must be even (aligned to re,im pairs)
/// - count: number of complex values to process (not doubles). The range is [offset, offset + 2*count) stepping by 2.
///
/// Preconditions:
/// - a != null
/// - offset >= 0, even, and offset <= a.length
/// - count >= 0 and offset + 2*count <= a.length
///
/// Numerical behavior:
/// - If any component is NaN, the result is NaN.
/// - If any component is infinite and none is NaN, the result is +Infinity.
    public static double norm2(double[] a, int offset, int count) {
        return Math.sqrt(norm2Sq(a, offset, count));
    }

    /// Computes the squared L2 norm (sum of squares of magnitudes) of all complex values in the array.
/// 
/// Convenience overload equivalent to norm2Sq(a, 0, a.length/2).
    public static double norm2Sq(double[] a) {
        if (a == null) throw new NullPointerException("array is null");
        if ((a.length & 1) != 0) {
            throw new IllegalArgumentException("array length must be even (re,im pairs)");
        }
        return norm2Sq(a, 0, a.length / 2);
    }

    /// Computes the squared L2 norm of count complex values starting at offset.
/// 
/// Robust single-pass implementation using the classic BLAS LASSQ scaling technique to avoid
/// overflow/underflow while accumulating sum of squares. No allocations are performed.
///
/// Layout: AoS \[re0, im0, re1, im1, …\]
/// Loop: for (i = offset; i < offset + 2*count; i += 2) touching re = a\[i\], im = a\[i+1\].
///
/// See preconditions in norm2(double[], int, int).
    public static double norm2Sq(double[] a, int offset, int count) {
        if (a == null) {
            throw new NullPointerException("array is null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if ((offset & 1) != 0) {
            throw new IllegalArgumentException("offset must be even (aligned to re,im pairs)");
        }
        if (offset > a.length) {
            throw new IllegalArgumentException("offset is out of bounds");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count (number of complex values) must be non-negative");
        }
        int remaining = a.length - offset;
        if (count > (remaining >> 1)) {
            throw new IllegalArgumentException("offset + 2*count exceeds array length");
        }

        // Robust LASSQ accumulation
        double scale = 0.0;
        double ssq = 1.0;
        int end = offset + (count << 1);
        for (int i = offset; i < end; i += 2) {
            double re = a[i];
            double im = a[i + 1];

            if (Double.isNaN(re) || Double.isNaN(im)) {
                return Double.NaN;
            }
            if (Double.isInfinite(re) || Double.isInfinite(im)) {
                return Double.POSITIVE_INFINITY;
            }

            double ar = Math.abs(re);
            if (ar != 0.0) {
                if (scale < ar) {
                    double r = scale / ar;
                    ssq = 1.0 + ssq * r * r;
                    scale = ar;
                } else if (scale != 0.0) {
                    double r = ar / scale;
                    ssq += r * r;
                } else {
                    // scale == 0 and ar != 0
                    scale = ar;
                    // ssq remains 1.0
                }
            }

            double ai = Math.abs(im);
            if (ai != 0.0) {
                if (scale < ai) {
                    double r = scale / ai;
                    ssq = 1.0 + ssq * r * r;
                    scale = ai;
                } else if (scale != 0.0) {
                    double r = ai / scale;
                    ssq += r * r;
                } else {
                    // scale == 0 and ai != 0
                    scale = ai;
                }
            }
        }

        return scale == 0.0 ? 0.0 : (scale * scale) * ssq;
    }

    /**
     * Scale a complex vector in-place by a complex scalar.
     *
     * Layout: AoS interleaved [re0, im0, re1, im1, …]. Offsets and lengths are in complex elements, not doubles.
     *
     * Aliasing: always safe (in-place only).
     *
     * Exceptions:
     * - NullPointerException if x is null
     * - IllegalArgumentException if nC < 0
     * - IndexOutOfBoundsException if the computed double-index range is out of bounds
     *
     * Numerics: IEEE-754 semantics; NaN/∞ propagate naturally.
     */
    public static void scal(double[] x, int xOffC, int nC, double alphaRe, double alphaIm) {
        if (x == null) throw new NullPointerException("x is null");
        if (nC < 0) throw new IllegalArgumentException("nC must be non-negative");
        int base = toDoubleIndex(xOffC);
        int end = base + (nC << 1);
        if (base < 0 || base > x.length || end < base || end > x.length) {
            throw new IndexOutOfBoundsException("x range is out of bounds");
        }
        for (int i = base; i < end; i += 2) {
            double xr = x[i];
            double xi = x[i + 1];
            double rr = alphaRe * xr - alphaIm * xi;
            double ri = alphaRe * xi + alphaIm * xr;
            x[i] = rr;
            x[i + 1] = ri;
        }
    }

    /**
     * y ← alpha * x + y for complex vectors.
     *
     * Layout: AoS interleaved [re0, im0, re1, im1, …]. Offsets and lengths are in complex elements.
     *
     * Aliasing: correct even when x and y are the same array or overlapping segments; loads occur before stores.
     *
     * Exceptions:
     * - NullPointerException if x or y is null
     * - IllegalArgumentException if nC < 0
     * - IndexOutOfBoundsException if any computed range is out of bounds
     */
    public static void axpy(double alphaRe, double alphaIm,
                            double[] x, int xOffC,
                            double[] y, int yOffC,
                            int nC) {
        if (x == null) throw new NullPointerException("x is null");
        if (y == null) throw new NullPointerException("y is null");
        if (nC < 0) throw new IllegalArgumentException("nC must be non-negative");
        int ix = toDoubleIndex(xOffC);
        int iy = toDoubleIndex(yOffC);
        int endx = ix + (nC << 1);
        int endy = iy + (nC << 1);
        if (ix < 0 || ix > x.length || endx < ix || endx > x.length) {
            throw new IndexOutOfBoundsException("x range is out of bounds");
        }
        if (iy < 0 || iy > y.length || endy < iy || endy > y.length) {
            throw new IndexOutOfBoundsException("y range is out of bounds");
        }
        // Determine safe iteration order when aliasing occurs within the same array.
        boolean sameArray = x == y;
        int step = 2;
        int startX = ix;
        int startY = iy;
        int count2 = nC << 1;
        if (sameArray) {
            int xStartC = xOffC;
            int yStartC = yOffC;
            int xEndC = xOffC + nC;
            int yEndC = yOffC + nC;
            boolean overlap = (xStartC < yEndC) && (yStartC < xEndC);
            if (overlap && xStartC < yStartC) {
                // x region starts before y region and overlaps: iterate backwards
                step = -2;
                startX = ix + (count2 - 2);
                startY = iy + (count2 - 2);
            }
        }

        for (int i = 0; i < count2; i += 2) {
            int xi = startX + i * (step / 2);
            int yi = startY + i * (step / 2);
            double xr = x[xi];
            double xi_im = x[xi + 1];
            double yr = y[yi];
            double yi_im = y[yi + 1];
            double tr = alphaRe * xr - alphaIm * xi_im;
            double ti = alphaRe * xi_im + alphaIm * xr;
            y[yi] = yr + tr;
            y[yi + 1] = yi_im + ti;
        }
    }

    /**
     * Elementwise complex multiply: out[i] ← x[i] * y[i]. Supports in-place out==x or out==y.
     *
     * Layout: AoS interleaved [re0, im0, re1, im1, …]. Offsets and lengths are in complex elements.
     *
     * Aliasing: out may be the same array slice as either x or y. It must not alias both inputs simultaneously
     * unless x and y refer to the exact same slice (same array and offsets). In that unsupported case, an
     * IllegalArgumentException is thrown.
     *
     * Exceptions:
     * - NullPointerException if any array is null
     * - IllegalArgumentException if nC < 0 or illegal aliasing occurs
     * - IndexOutOfBoundsException if any computed range is out of bounds
     */
    public static void mul(double[] x, int xOffC,
                           double[] y, int yOffC,
                           double[] out, int outOffC,
                           int nC) {
        if (x == null) throw new NullPointerException("x is null");
        if (y == null) throw new NullPointerException("y is null");
        if (out == null) throw new NullPointerException("out is null");
        if (nC < 0) throw new IllegalArgumentException("nC must be non-negative");

        int ix = toDoubleIndex(xOffC);
        int iy = toDoubleIndex(yOffC);
        int io = toDoubleIndex(outOffC);
        int len2 = nC << 1;
        int endx = ix + len2;
        int endy = iy + len2;
        int endo = io + len2;
        if (ix < 0 || ix > x.length || endx < ix || endx > x.length) {
            throw new IndexOutOfBoundsException("x range is out of bounds");
        }
        if (iy < 0 || iy > y.length || endy < iy || endy > y.length) {
            throw new IndexOutOfBoundsException("y range is out of bounds");
        }
        if (io < 0 || io > out.length || endo < io || endo > out.length) {
            throw new IndexOutOfBoundsException("out range is out of bounds");
        }

        boolean outIsX = out == x;
        boolean outIsY = out == y;
        if (outIsX && outIsY) {
            boolean sameSlice = (x == y) && (xOffC == yOffC) && (xOffC == outOffC);
            if (!sameSlice) {
                throw new IllegalArgumentException("out cannot alias both x and y unless slices are identical");
            }
        }

        for (int i = 0; i < len2; i += 2) {
            int xi = ix + i;
            int yi = iy + i;
            int oi = io + i;
            double xr = x[xi];
            double xi_im = x[xi + 1];
            double yr = y[yi];
            double yi_im = y[yi + 1];
            double rr = xr * yr - xi_im * yi_im;
            double ri = xr * yi_im + xi_im * yr;
            out[oi] = rr;
            out[oi + 1] = ri;
        }
    }

}
