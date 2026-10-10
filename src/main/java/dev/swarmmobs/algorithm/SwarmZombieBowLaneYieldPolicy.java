package dev.swarmmobs.algorithm;

import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import java.util.List;

/**
 * Minecraft-only cooperative movement: a frontline Zombie can select a
 * nearby game square outside a same-target Skeleton's bow corridor.
 * This does not affect the vanilla Zombie attack or Skeleton arrows.
 */
public final class SwarmZombieBowLaneYieldPolicy {
    public static final double MELEE_PROTECTION_BLOCKS = 4.5;
    public static final double SIDESTEP_BLOCKS = 2.5;
    public static final double MAX_SHOOTER_DISTANCE_BLOCKS = 18.0;

    public record Options(Vec2 left, Vec2 right, boolean applicable) {}

    public static Options propose(Vec2 zombie, Vec2 planned, Vec2 shooter,
            Vec2 directlyVisiblePlayer, boolean coordinating,
            double observationConfidence) {
        Options no = new Options(planned, planned, false);
        if (!coordinating || !finite(zombie) || !finite(planned)
                || !finite(shooter) || !finite(directlyVisiblePlayer)
                || !Double.isFinite(observationConfidence)
                || observationConfidence < 0.7
                || zombie.subtract(directlyVisiblePlayer).length()
                        <= MELEE_PROTECTION_BLOCKS
                || zombie.subtract(shooter).length()
                        > MAX_SHOOTER_DISTANCE_BLOCKS
                || SwarmFriendlyFireLanePolicy.isClear(shooter,
                        directlyVisiblePlayer, List.of(planned))) {
            return no;
        }
        Vec2 line = directlyVisiblePlayer.subtract(shooter);
        double length = line.length();
        if (!Double.isFinite(length) || length < 2.0) return no;
        Vec2 perpendicular = new Vec2(-line.z() / length, line.x() / length);
        return new Options(
                planned.add(perpendicular.scale(SIDESTEP_BLOCKS)),
                planned.add(perpendicular.scale(-SIDESTEP_BLOCKS)),
                true);
    }

    /** Stable slot chooses a side only if the real Minecraft square is usable. */
    public static Vec2 choose(Options options, boolean leftValid,
            boolean rightValid, int stableSlot) {
        if (options == null || !options.applicable()) return null;
        if (leftValid && rightValid) {
            return Math.floorMod(stableSlot, 2) == 0
                    ? options.left() : options.right();
        }
        if (leftValid) return options.left();
        if (rightValid) return options.right();
        return null;
    }

    private static boolean finite(Vec2 p) {
        return p != null && Double.isFinite(p.x()) && Double.isFinite(p.z());
    }

    private SwarmZombieBowLaneYieldPolicy() {}
}
