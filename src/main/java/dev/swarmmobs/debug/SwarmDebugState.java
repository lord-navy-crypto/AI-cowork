package dev.swarmmobs.debug;

public final class SwarmDebugState {
    private static volatile boolean particlesEnabled;

    public static boolean particlesEnabled() {
        return particlesEnabled;
    }

    public static boolean toggleParticles() {
        particlesEnabled = !particlesEnabled;
        return particlesEnabled;
    }

    public static void setParticlesEnabled(boolean enabled) {
        particlesEnabled = enabled;
    }

    private SwarmDebugState() {}
}
