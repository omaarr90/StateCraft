package com.omaarr90.statecraft.quantum;

import com.omaarr90.statecraft.core.math.ComplexNumber;

public record PauliX() implements SingleQubitGate {

    private static final ComplexNumber M00 = ComplexNumber.zero();
    private static final ComplexNumber M01 = ComplexNumber.one();
    private static final ComplexNumber M10 = ComplexNumber.one();
    private static final ComplexNumber M11 = ComplexNumber.zero();

    @Override
    public String name() {
        return "X";
    }

    @Override
    public ComplexNumber element(int row, int col) {
        return switch (row) {
            case 0 -> switch (col) {
                case 0 -> M00;
                case 1 -> M01;
                default -> throw new IllegalArgumentException("col must be 0 or 1: " + col);
            };
            case 1 -> switch (col) {
                case 0 -> M10;
                case 1 -> M11;
                default -> throw new IllegalArgumentException("col must be 0 or 1: " + col);
            };
            default -> throw new IllegalArgumentException("row must be 0 or 1: " + row);
        };
    }
}
