package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentProfile;
import dev.swarmmobs.agent.SwarmRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeterogeneousSwarmPlannerTest {

    @Test
    void rangedSupportUsesRearStandoffInsteadOfMeleeDestination() {
        SwarmAgentProfile support = SwarmAgentProfile.rangedSupport();
        double supportRadius = 4.5 * support.formationRadiusMultiplier();

        SwarmCombatPlanner.Plan ranged = SwarmCombatPlanner.planForRoleWithMotion(
                SwarmRole.RANGED_SUPPORT,
                3,
                new SwarmCombatPlanner.Vec2(0.0, 0.0),
                new SwarmCombatPlanner.Vec2(0.0, 0.0),
                new SwarmCombatPlanner.Vec2(10.0, 0.0),
                new SwarmCombatPlanner.Vec2(1.0, 0.0),
                List.of(),
                List.of(),
                8,
                supportRadius,
                2.4,
                0.0,
                0.0,
                0.0,
                3.0
        );

        assertEquals(SwarmRole.RANGED_SUPPORT, ranged.role());
        assertTrue(ranged.destination().x() < 10.0);
        assertTrue(
                ranged.destination().subtract(new SwarmCombatPlanner.Vec2(10.0, 0.0)).length()
                        >= supportRadius - 0.01
        );
    }

    @Test
    void assaultAndRangedSupportProduceDifferentTacticalDestinations() {
        var target = new SwarmCombatPlanner.Vec2(10.0, 0.0);
        var forward = new SwarmCombatPlanner.Vec2(1.0, 0.0);

        SwarmCombatPlanner.Plan assault = SwarmCombatPlanner.planForRoleWithMotion(
                SwarmRole.CHASER,
                0,
                new SwarmCombatPlanner.Vec2(0.0, 0.0),
                new SwarmCombatPlanner.Vec2(0.0, 0.0),
                target,
                forward,
                List.of(),
                List.of(),
                8,
                4.5,
                2.4,
                0.0,
                0.0,
                0.0,
                3.0
        );

        SwarmCombatPlanner.Plan support = SwarmCombatPlanner.planForRoleWithMotion(
                SwarmRole.RANGED_SUPPORT,
                0,
                new SwarmCombatPlanner.Vec2(0.0, 0.0),
                new SwarmCombatPlanner.Vec2(0.0, 0.0),
                target,
                forward,
                List.of(),
                List.of(),
                8,
                7.875,
                2.4,
                0.0,
                0.0,
                0.0,
                3.0
        );

        assertNotEquals(assault.destination(), support.destination());
        assertTrue(assault.destination().subtract(target).length() < 1.0);
        assertTrue(support.destination().subtract(target).length() > 7.0);
    }
}
