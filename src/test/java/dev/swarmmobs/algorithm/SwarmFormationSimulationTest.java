package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Plan;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmFormationSimulationTest {

    @Test
    void eightAgentsPopulateAllFourRolesAndSpreadAcrossFormation() {
        Vec2 target = new Vec2(20.0, 20.0);
        Vec2 forward = new Vec2(0.0, 1.0);

        Map<SwarmRole, Integer> roleCounts = new EnumMap<>(SwarmRole.class);
        Set<String> uniqueDestinations = new HashSet<>();

        for (long i = 0; i < 8; i++) {
            UUID agentId = new UUID(0L, i);
            Plan plan = SwarmCombatPlanner.plan(
                    agentId,
                    new Vec2(i, 0.0),
                    target,
                    forward,
                    List.of(),
                    8,
                    4.5,
                    2.4,
                    0.0,
                    0.0
            );

            roleCounts.merge(plan.role(), 1, Integer::sum);
            uniqueDestinations.add(key(plan.destination()));
        }

        assertEquals(2, roleCounts.getOrDefault(SwarmRole.CHASER, 0));
        assertEquals(2, roleCounts.getOrDefault(SwarmRole.FLANK_LEFT, 0));
        assertEquals(2, roleCounts.getOrDefault(SwarmRole.FLANK_RIGHT, 0));
        assertEquals(2, roleCounts.getOrDefault(SwarmRole.REAR_PRESSURE, 0));

        // Lane offsets keep two agents with the same role from collapsing onto one point.
        assertEquals(8, uniqueDestinations.size());
    }

    @Test
    void localSeparationChangesDestinationWithoutChangingAssignedRole() {
        UUID agentId = new UUID(0L, 1L);
        Vec2 self = new Vec2(0.0, 0.0);
        Vec2 target = new Vec2(10.0, 0.0);

        Plan withoutNeighbor = SwarmCombatPlanner.plan(
                agentId,
                self,
                target,
                new Vec2(1.0, 0.0),
                List.of(),
                8,
                4.5,
                2.4,
                2.0,
                0.0
        );

        Plan withCloseNeighbor = SwarmCombatPlanner.plan(
                agentId,
                self,
                target,
                new Vec2(1.0, 0.0),
                List.of(new Vec2(1.0, 0.0)),
                8,
                4.5,
                2.4,
                2.0,
                0.0
        );

        assertEquals(withoutNeighbor.role(), withCloseNeighbor.role());
        assertNotEquals(key(withoutNeighbor.destination()), key(withCloseNeighbor.destination()));
        assertTrue(withCloseNeighbor.separationMagnitude() > 0.0);
    }

    private static String key(Vec2 point) {
        return String.format(java.util.Locale.ROOT, "%.6f,%.6f", point.x(), point.z());
    }
}
