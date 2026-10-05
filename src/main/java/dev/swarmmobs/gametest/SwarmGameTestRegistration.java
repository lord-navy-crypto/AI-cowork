package dev.swarmmobs.gametest;

import net.minecraft.gametest.framework.GameTestRegistry;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

public final class SwarmGameTestRegistration {
    public static void register(RegisterGameTestsEvent event) {
        try {
            GameTestRegistry.register(SwarmRuntimeGameTests.class);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to register Swarm Mobs GameTests", exception);
        }
    }

    private SwarmGameTestRegistration() {}
}
