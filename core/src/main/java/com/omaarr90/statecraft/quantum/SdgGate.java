package com.omaarr90.statecraft.quantum;

import com.omaarr90.statecraft.core.math.ComplexNumber;

public record SdgGate() implements SingleQubitGate {

    private static final ComplexNumber ZERO = ComplexNumber.zero();
    private static final ComplexNumber ONE = ComplexNumber.one();
    private static final ComplexNumber NEG_I = new ComplexNumber(0.0, -1.0);

    @Override
    public String name() {
        return "Sdg";
    }

    @Override
    public ComplexNumber element(int row, int col) {
        return switch (row) {
            case 0 -> switch (col) {
                case 0 -> ONE;
                case 1 -> ZERO;
                default -> throw invalidColumn(col);
            };
            case 1 -> switch (col) {
                case 0 -> ZERO;
                case 1 -> NEG_I;
                default -> throw invalidColumn(col);
            };
            default -> throw invalidRow(row);
        };
    }

    private IllegalArgumentException invalidRow(int row) {
        return new IllegalArgumentException("row must be 0 or 1, got " + row);
    }

    private IllegalArgumentException invalidColumn(int col) {
        return new IllegalArgumentException("col must be 0 or 1, got " + col);
    }
}
