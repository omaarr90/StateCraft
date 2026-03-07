package com.omaarr90.statecraft.engines.stabilizer;

import java.util.Arrays;
import java.util.SplittableRandom;

final class StabilizerTableau {

    private final int qubitCount;
    private final int rowCount;
    private final int wordCount;
    private final long[] x;
    private final long[] z;
    private final boolean[] phase;

    StabilizerTableau(int qubitCount) {
        this.qubitCount = qubitCount;
        this.rowCount = (qubitCount << 1) + 1;
        this.wordCount = (qubitCount + Long.SIZE - 1) / Long.SIZE;
        this.x = new long[rowCount * wordCount];
        this.z = new long[rowCount * wordCount];
        this.phase = new boolean[rowCount];

        for (int qubit = 0; qubit < qubitCount; qubit++) {
            setX(qubit, qubit, true);
            setZ(qubitCount + qubit, qubit, true);
        }
    }

    private StabilizerTableau(
            int qubitCount,
            int rowCount,
            int wordCount,
            long[] x,
            long[] z,
            boolean[] phase) {
        this.qubitCount = qubitCount;
        this.rowCount = rowCount;
        this.wordCount = wordCount;
        this.x = x;
        this.z = z;
        this.phase = phase;
    }

    StabilizerTableau copy() {
        return new StabilizerTableau(
                qubitCount,
                rowCount,
                wordCount,
                x.clone(),
                z.clone(),
                phase.clone());
    }

    void setBasisStateQubits(int[] qubits) {
        for (int qubit : qubits) {
            phase[qubitCount + qubit] = true;
        }
    }

    void applyHadamard(int qubit) {
        for (int row = 0; row < (qubitCount << 1); row++) {
            boolean xBit = getX(row, qubit);
            boolean zBit = getZ(row, qubit);
            if (xBit && zBit) {
                phase[row] = !phase[row];
            }
            setX(row, qubit, zBit);
            setZ(row, qubit, xBit);
        }
    }

    void applyS(int qubit) {
        for (int row = 0; row < (qubitCount << 1); row++) {
            boolean xBit = getX(row, qubit);
            boolean zBit = getZ(row, qubit);
            if (xBit && zBit) {
                phase[row] = !phase[row];
            }
            setZ(row, qubit, zBit ^ xBit);
        }
    }

    void applySdg(int qubit) {
        for (int row = 0; row < (qubitCount << 1); row++) {
            boolean xBit = getX(row, qubit);
            boolean zBit = getZ(row, qubit);
            if (xBit && !zBit) {
                phase[row] = !phase[row];
            }
            setZ(row, qubit, zBit ^ xBit);
        }
    }

    void applyX(int qubit) {
        for (int row = 0; row < (qubitCount << 1); row++) {
            if (getZ(row, qubit)) {
                phase[row] = !phase[row];
            }
        }
    }

    void applyY(int qubit) {
        for (int row = 0; row < (qubitCount << 1); row++) {
            if (getX(row, qubit) ^ getZ(row, qubit)) {
                phase[row] = !phase[row];
            }
        }
    }

    void applyZ(int qubit) {
        for (int row = 0; row < (qubitCount << 1); row++) {
            if (getX(row, qubit)) {
                phase[row] = !phase[row];
            }
        }
    }

    void applyCnot(int control, int target) {
        for (int row = 0; row < (qubitCount << 1); row++) {
            boolean xControl = getX(row, control);
            boolean zControl = getZ(row, control);
            boolean xTarget = getX(row, target);
            boolean zTarget = getZ(row, target);
            if (xControl && zTarget && !(xTarget ^ zControl)) {
                phase[row] = !phase[row];
            }
            setX(row, target, xTarget ^ xControl);
            setZ(row, control, zControl ^ zTarget);
        }
    }

    void applyCz(int control, int target) {
        applyHadamard(target);
        applyCnot(control, target);
        applyHadamard(target);
    }

    void applySwap(int first, int second) {
        for (int row = 0; row < (qubitCount << 1); row++) {
            swapBits(x, row, first, second);
            swapBits(z, row, first, second);
        }
    }

    int measureZ(int qubit, SplittableRandom rng) {
        int pivot = -1;
        for (int row = qubitCount; row < (qubitCount << 1); row++) {
            if (getX(row, qubit)) {
                pivot = row;
                break;
            }
        }

        if (pivot == -1) {
            int scratch = qubitCount << 1;
            clearRow(scratch);
            for (int row = 0; row < qubitCount; row++) {
                if (getX(row, qubit)) {
                    rowSum(scratch, qubitCount + row);
                }
            }
            return phase[scratch] ? 1 : 0;
        }

        for (int row = 0; row < (qubitCount << 1); row++) {
            if (row != pivot && getX(row, qubit)) {
                rowSum(row, pivot);
            }
        }
        copyRow(pivot, pivot - qubitCount);
        clearRow(pivot);
        setZ(pivot, qubit, true);
        phase[pivot] = rng.nextBoolean();
        return phase[pivot] ? 1 : 0;
    }

    private void rowSum(int targetRow, int sourceRow) {
        int phaseAccumulator = (phase[targetRow] ? 2 : 0) + (phase[sourceRow] ? 2 : 0);
        for (int qubit = 0; qubit < qubitCount; qubit++) {
            phaseAccumulator += phaseContribution(
                    getX(sourceRow, qubit),
                    getZ(sourceRow, qubit),
                    getX(targetRow, qubit),
                    getZ(targetRow, qubit));
        }

        int targetBase = targetRow * wordCount;
        int sourceBase = sourceRow * wordCount;
        for (int word = 0; word < wordCount; word++) {
            x[targetBase + word] ^= x[sourceBase + word];
            z[targetBase + word] ^= z[sourceBase + word];
        }

        int normalized = Math.floorMod(phaseAccumulator, 4);
        phase[targetRow] = normalized >= 2;
    }

    private void copyRow(int sourceRow, int targetRow) {
        int sourceBase = sourceRow * wordCount;
        int targetBase = targetRow * wordCount;
        System.arraycopy(x, sourceBase, x, targetBase, wordCount);
        System.arraycopy(z, sourceBase, z, targetBase, wordCount);
        phase[targetRow] = phase[sourceRow];
    }

    private void clearRow(int row) {
        int base = row * wordCount;
        Arrays.fill(x, base, base + wordCount, 0L);
        Arrays.fill(z, base, base + wordCount, 0L);
        phase[row] = false;
    }

    private boolean getX(int row, int qubit) {
        return getBit(x, row, qubit);
    }

    private boolean getZ(int row, int qubit) {
        return getBit(z, row, qubit);
    }

    private void setX(int row, int qubit, boolean value) {
        setBit(x, row, qubit, value);
    }

    private void setZ(int row, int qubit, boolean value) {
        setBit(z, row, qubit, value);
    }

    private boolean getBit(long[] values, int row, int qubit) {
        int word = wordIndex(qubit);
        long mask = bitMask(qubit);
        return (values[(row * wordCount) + word] & mask) != 0L;
    }

    private void setBit(long[] values, int row, int qubit, boolean value) {
        int index = (row * wordCount) + wordIndex(qubit);
        long mask = bitMask(qubit);
        if (value) {
            values[index] |= mask;
        } else {
            values[index] &= ~mask;
        }
    }

    private void swapBits(long[] values, int row, int first, int second) {
        boolean firstBit = getBit(values, row, first);
        boolean secondBit = getBit(values, row, second);
        if (firstBit == secondBit) {
            return;
        }
        setBit(values, row, first, secondBit);
        setBit(values, row, second, firstBit);
    }

    private static int wordIndex(int qubit) {
        return qubit >>> 6;
    }

    private static long bitMask(int qubit) {
        return 1L << (qubit & 63);
    }

    private static int phaseContribution(boolean x1, boolean z1, boolean x2, boolean z2) {
        if (!x1 && !z1) {
            return 0;
        }
        if (x1 && z1) {
            return (z2 ? 1 : 0) - (x2 ? 1 : 0);
        }
        if (x1) {
            return z2 ? (x2 ? 1 : -1) : 0;
        }
        return x2 ? (z2 ? -1 : 1) : 0;
    }
}
