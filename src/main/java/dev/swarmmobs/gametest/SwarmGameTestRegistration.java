package dev.swarmmobs.gametest;

import net.neoforged.neoforge.event.RegisterGameTestsEvent;

public final class SwarmGameTestRegistration {
    public static void register(RegisterGameTestsEvent event) {
        event.register(SwarmRuntimeGameTests.class);
    }

    private SwarmGameTestRegistration() {}
}
