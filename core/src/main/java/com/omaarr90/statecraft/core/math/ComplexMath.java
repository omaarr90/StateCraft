package com.omaarr90.statecraft.core.math;

/**
 * Tiny scalar complex helpers (no arrays).
 * <p>
 * Pure functions operating on individual complex numbers represented by their real and imaginary parts.
 * Used by tests and optionally by array ops.
 */
public final class ComplexMath {

    private ComplexMath() {}

    /** Simple immutable carrier for a complex value. */
    public static record C(double re, double im) { }

    /**
     * Multiplies two complex numbers (ar + i ai) * (br + i bi).
     *
     * Numeric behavior follows IEEE-754 for doubles: NaNs propagate; infinities behave per hardware.
     */
    public static C mul(double ar, double ai, double br, double bi) {
        double rr = ar * br - ai * bi;
        double ri = ar * bi + ai * br;
        return new C(rr, ri);
    }
}
