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
}
