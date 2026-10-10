package dev.swarmmobs.algorithm;

import java.util.UUID;

/**
 * Minimal close-range blast awareness for mixed Zombie/Creeper squads.
 * Never changes explosion physics, fuse duration or attacking behavior.
 * An idle/active frontline Zombie yields movement only to a real ignited or
 * swelling SAME-TARGET Creeper in a small local radius.
 */
public final class SwarmBreacherSafetyPolicy {
    public static final double ALERT_RANGE = 5.0;
    public static final double ESCAPE_STEP = 4.5;

    public static boolean mustYield(UUID zombieTarget, UUID creeperTarget,
                                    boolean swelling, boolean ignited,
                                    double distanceSquared) {
        return zombieTarget != null && zombieTarget.equals(creeperTarget)
                && (swelling || ignited)
                && Double.isFinite(distanceSquared)
                && distanceSquared <= ALERT_RANGE * ALERT_RANGE;
    }

    public static SwarmCombatPlanner.Vec2 stepAway(
            SwarmCombatPlanner.Vec2 worker,
            SwarmCombatPlanner.Vec2 creeper, long stableSeed) {
        if (worker == null || creeper == null
                || !Double.isFinite(worker.x()) || !Double.isFinite(worker.z())
                || !Double.isFinite(creeper.x()) || !Double.isFinite(creeper.z())) {
            return null;
        }
        double dx = worker.x() - creeper.x();
        double dz = worker.z() - creeper.z();
        double length = Math.hypot(dx,dz);
        if (length < 0.1) {
            double angle = (Math.floorMod(stableSeed,8L) * Math.PI / 4.0);
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            length = 1.0;
        }
        return new SwarmCombatPlanner.Vec2(
                worker.x() + ESCAPE_STEP * dx / length,
                worker.z() + ESCAPE_STEP * dz / length);
    }

    private SwarmBreacherSafetyPolicy() {}
}
