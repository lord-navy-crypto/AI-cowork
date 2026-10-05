package dev.swarmmobs.gametest;

import net.minecraft.gametest.framework.GameTestRegistry;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

public final class SwarmGameTestRegistration {
    public static void register(RegisterGameTestsEvent event) {
        GameTestRegistry.register(SwarmRuntimeGameTests.class);
    }

    private SwarmGameTestRegistration() {}
}
