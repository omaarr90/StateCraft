package com.omaarr90.statecraft;

import com.omaarr90.statecraft.core.engine.SimulatorEngine;

import java.util.ServiceLoader;

public class Main {
    void main() {
        IO.println("Statecraft CLI 0.1 – engines discovered:");

        var loader = ServiceLoader.load(SimulatorEngine.class);
        int count = 0;
        for (var engine : loader) {
            System.out.println("  - " + engine.id());
            count++;
        }
        if (count == 0) {
            System.out.println("  (none yet — add an engine in Phase 3)");
        }

    }
}

