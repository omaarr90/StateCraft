package com.omaarr90.statecraft;

import com.omaarr90.statecraft.core.engine.SimulatorEngine;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

@Command(
        name = "statecraft",
        description = "Run Statecraft commands.",
        mixinStandardHelpOptions = true,
        version = StatecraftCli.VERSION,
        subcommands = StatecraftCli.Engines.class
)
public final class StatecraftCli implements Callable<Integer> {
    static final String VERSION = "Statecraft CLI 0.1";

    @Spec
    private CommandSpec spec;

    void main(String[] args) {
        int exitCode = new CommandLine(new StatecraftCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return CommandLine.ExitCode.OK;
    }

    @Command(name = "engines", description = "List available simulator engines.")
    static final class Engines implements Callable<Integer> {
        @Spec
        private CommandSpec spec;

        @Override
        public Integer call() {
            var loader = ServiceLoader.load(SimulatorEngine.class);
            var engines = new ArrayList<String>();
            for (var engine : loader) {
                engines.add(engine.id());
            }
            engines.sort(Comparator.naturalOrder());

            var out = spec.commandLine().getOut();
            out.println(VERSION + " – engines discovered:");
            if (engines.isEmpty()) {
                out.println("  (none yet — add an engine in Phase 3)");
            } else {
                for (var id : engines) {
                    out.println("  - " + id);
                }
            }
            out.flush();
            return CommandLine.ExitCode.OK;
        }
    }
}
