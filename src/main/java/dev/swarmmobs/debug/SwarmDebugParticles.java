package dev.swarmmobs.debug;

import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.data.SwarmAttachments;
import dev.swarmmobs.algorithm.TargetObservation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;

import java.util.List;

public final class SwarmDebugParticles {
    private static final int LINE_STEPS = 6;

    public static void render(
            ServerLevel level,
            PathfinderMob mob,
            List<PathfinderMob> neighbors
    ) {
        if (!SwarmDebugState.particlesEnabled()) {
            return;
        }

        SwarmAgentState state = mob.getData(SwarmAttachments.AGENT_STATE.get());

        double ax = mob.getX();
        double ay = mob.getY() + 1.1;
        double az = mob.getZ();

        if (state.hasDestination()) {
            drawLine(
                    level,
                    ax, ay, az,
                    state.destinationX(), mob.getY() + 0.15, state.destinationZ(),
                    ParticleTypes.END_ROD
            );

            level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    state.destinationX(),
                    mob.getY() + 0.3,
                    state.destinationZ(),
                    2,
                    0.08, 0.08, 0.08,
                    0.0
            );
        }

        if (state.hasNavigationWaypoint()
                && state.navigationMode() != dev.swarmmobs.agent.SwarmNavigationMode.PLAN) {
            drawLine(
                    level,
                    ax, ay, az,
                    state.navigationWaypointX(), mob.getY() + 0.45, state.navigationWaypointZ(),
                    ParticleTypes.ELECTRIC_SPARK
            );
            level.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    state.navigationWaypointX(),
                    mob.getY() + 0.45,
                    state.navigationWaypointZ(),
                    state.navigationMode() == dev.swarmmobs.agent.SwarmNavigationMode.RECOVERY ? 4 : 2,
                    0.06, 0.06, 0.06,
                    0.0
            );
        }

        int rendered = 0;
        for (PathfinderMob neighbor : neighbors) {
            if (rendered >= 3) {
                break;
            }

            drawLine(
                    level,
                    ax, ay, az,
                    neighbor.getX(), neighbor.getY() + 1.1, neighbor.getZ(),
                    ParticleTypes.ELECTRIC_SPARK
            );
            rendered++;
        }

        TargetObservation observation = state.targetObservation();
        if (observation != null && observation.hasFinitePosition()) {
            level.sendParticles(
                    ParticleTypes.CRIT,
                    observation.x(),
                    observation.y() + 1.6,
                    observation.z(),
                    state.directObservation() ? 2 : 1,
                    0.05, 0.05, 0.05,
                    0.0
            );
        }
    }

    private static void drawLine(
            ServerLevel level,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            net.minecraft.core.particles.ParticleOptions particle
    ) {
        for (int i = 1; i <= LINE_STEPS; i++) {
            double t = i / (double) LINE_STEPS;
            level.sendParticles(
                    particle,
                    lerp(x1, x2, t),
                    lerp(y1, y2, t),
                    lerp(z1, z2, t),
                    1,
                    0.0, 0.0, 0.0,
                    0.0
            );
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private SwarmDebugParticles() {}
}
