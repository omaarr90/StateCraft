package com.omaarr90.statecraft.core.noise;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Composite error channel that applies multiple channels sequentially.
 * <p>
 * This allows building complex noise models from simpler components.
 * Note: The Kraus decomposition of a composite channel is the tensor
 * product of the individual decompositions, but for Monte Carlo simulation
 * we apply each channel's sampled operator sequentially.
 * <p>
 * Current limitation: All channels must act on the same set of qubits,
 * or this implementation will union the affected qubits.
 */
public final class CompositeChannel implements ErrorChannel {

    private final ErrorChannel[] channels;
    private final int[] affectedQubits;

    CompositeChannel(ErrorChannel... channels) {
        if (channels.length == 0) {
            throw new IllegalArgumentException("composite channel requires at least one channel");
        }
        this.channels = Arrays.copyOf(channels, channels.length);

        // Compute union of all affected qubits
        Set<Integer> qubitSet = new HashSet<>();
        for (ErrorChannel channel : channels) {
            for (int q : channel.affectedQubits()) {
                qubitSet.add(q);
            }
        }
        this.affectedQubits = qubitSet.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    @Override
    public KrausDecomposition krausDecomposition() {
        // For composite channels in Monte Carlo simulation, we don't need
        // the full Kraus decomposition. The engine will sample and apply
        // each component channel sequentially.
        throw new UnsupportedOperationException(
                "CompositeChannel does not provide a single Kraus decomposition. "
                        + "Use getChannels() for sequential application.");
    }

    @Override
    public int[] affectedQubits() {
        return affectedQubits.clone();
    }

    /**
     * Returns the component channels to be applied sequentially.
     */
    public ErrorChannel[] getChannels() {
        return Arrays.copyOf(channels, channels.length);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof CompositeChannel other)) return false;
        return Arrays.equals(channels, other.channels);
    }

    @Override
    public int hashCode() {
        return Objects.hash((Object[]) channels);
    }

    @Override
    public String toString() {
        return "CompositeChannel[channels=" + Arrays.toString(channels) + "]";
    }
}
