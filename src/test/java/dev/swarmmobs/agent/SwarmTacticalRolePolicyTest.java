package dev.swarmmobs.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmTacticalRolePolicyTest {

    @Test
    void rangedSupportNeverConsumesMeleeRoleSlots() {
        for (int slot = 0; slot < 16; slot++) {
            assertEquals(
                    SwarmRole.RANGED_SUPPORT,
                    SwarmTacticalRolePolicy.roleFor(SwarmAgentArchetype.RANGED_SUPPORT, slot)
            );
        }
    }

    @Test
    void assaultAgentsRetainFourRoleCycle() {
        assertEquals(SwarmRole.CHASER, SwarmTacticalRolePolicy.roleFor(SwarmAgentArchetype.ASSAULT, 0));
        assertEquals(SwarmRole.FLANK_LEFT, SwarmTacticalRolePolicy.roleFor(SwarmAgentArchetype.ASSAULT, 1));
        assertEquals(SwarmRole.FLANK_RIGHT, SwarmTacticalRolePolicy.roleFor(SwarmAgentArchetype.ASSAULT, 2));
        assertEquals(SwarmRole.REAR_PRESSURE, SwarmTacticalRolePolicy.roleFor(SwarmAgentArchetype.ASSAULT, 3));
    }
    @Test
    void breacherAlwaysOwnsDirectPressureRole() {
        for (int slot = 0; slot < 16; slot++) {
            assertEquals(
                    SwarmRole.CHASER,
                    SwarmTacticalRolePolicy.roleFor(SwarmAgentArchetype.BREACHER, slot)
            );
        }
    }

    @Test
    void flankerArchetypeAlternatesOnlyBetweenSideRoles() {
        for (int slot = 0; slot < 12; slot++) {
            SwarmRole role = SwarmTacticalRolePolicy.roleFor(SwarmAgentArchetype.FLANKER, slot);
            assertTrue(role == SwarmRole.FLANK_LEFT || role == SwarmRole.FLANK_RIGHT);
        }

        assertEquals(
                SwarmRole.FLANK_LEFT,
                SwarmTacticalRolePolicy.roleFor(SwarmAgentArchetype.FLANKER, 0)
        );
        assertEquals(
                SwarmRole.FLANK_RIGHT,
                SwarmTacticalRolePolicy.roleFor(SwarmAgentArchetype.FLANKER, 1)
        );
    }
    @Test
    void assaultStopsCompetingForFlanksWhenTwoDedicatedFlankersAreLocal() {
        SwarmLocalComposition composition = new SwarmLocalComposition(3, 1, 2);

        for (int slot = 0; slot < 12; slot++) {
            SwarmRole role = SwarmTacticalRolePolicy.roleFor(
                    SwarmAgentArchetype.ASSAULT,
                    slot,
                    composition
            );
            assertTrue(role == SwarmRole.CHASER || role == SwarmRole.REAR_PRESSURE);
        }
    }

    @Test
    void assaultKeepsFullRoleCycleWithoutDedicatedFlankCoverage() {
        SwarmLocalComposition composition = new SwarmLocalComposition(4, 1, 1);

        assertEquals(
                SwarmRole.FLANK_LEFT,
                SwarmTacticalRolePolicy.roleFor(
                        SwarmAgentArchetype.ASSAULT,
                        1,
                        composition
                )
        );
        assertEquals(
                SwarmRole.FLANK_RIGHT,
                SwarmTacticalRolePolicy.roleFor(
                        SwarmAgentArchetype.ASSAULT,
                        2,
                        composition
                )
        );
    }

}
