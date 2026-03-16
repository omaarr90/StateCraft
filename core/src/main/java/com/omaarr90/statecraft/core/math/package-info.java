package com.omaarr90.statecraft.core.math;

/**
 * Core math utilities for interleaved complex arrays.
 *
 * Layout - Arrays are AoS interleaved: [re0, im0, re1, im1, …]. - Offsets
 * (e.g., xOffC) and lengths (nC) are expressed in complex elements, not
 * doubles. To convert an offset in complex units to the underlying double
 * index, multiply by 2.
 *
 * Aliasing - Methods document their aliasing guarantees. Generally, operations
 * are correct when inputs/outputs reference the same array or overlapping
 * slices, provided loads are performed before stores. - For elementwise
 * multiply, out may alias x or y, but not both simultaneously unless x and y
 * refer to the exact same slice (same array and same offset).
 *
 * Exceptions - Null arrays cause NullPointerException. - Negative lengths (nC <
 * 0) cause IllegalArgumentException. - Any out-of-bounds slice (after
 * converting offsets/lengths to double indices) causes
 * IndexOutOfBoundsException.
 *
 * Numerics - No special handling of NaN/∞ beyond IEEE-754. NaNs propagate
 * naturally; infinities behave per double rules.
 */
