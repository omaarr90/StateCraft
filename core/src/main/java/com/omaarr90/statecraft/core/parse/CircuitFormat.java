package com.omaarr90.statecraft.core.parse;

import java.util.Locale;
import java.util.Objects;

public enum CircuitFormat {
    JSON("json"),
    QASM("qasm"),
    AUTO("auto");

    private final String id;

    CircuitFormat(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static CircuitFormat fromOption(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (CircuitFormat format : values()) {
            if (format.id.equals(normalized)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported format: " + value);
    }
}
