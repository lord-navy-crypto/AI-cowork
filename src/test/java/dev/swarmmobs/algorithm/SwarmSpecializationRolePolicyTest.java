package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.agent.SwarmSpecialization;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmSpecializationRolePolicyTest {

    @Test
    void engineeringAndReserveStayBehindVanguard() {
        assertEquals(
                SwarmRole.REAR_PRESSURE,
                SwarmSpecializationRolePolicy.role(
                        SwarmSpecialization.ENGINEER,
                        SwarmRole.CHASER
                )
        );
        assertEquals(
                SwarmRole.CHASER,
                SwarmSpecializationRolePolicy.role(
                        SwarmSpecialization.VANGUARD,
                        SwarmRole.REAR_PRESSURE
                )
        );
    }

    @Test
    void overwatchStaysRangedAndDeeperThanSuppressor() {
        assertEquals(
                SwarmRole.RANGED_SUPPORT,
                SwarmSpecializationRolePolicy.role(
                        SwarmSpecialization.OVERWATCH,
                        SwarmRole.CHASER
                )
        );
        assertTrue(
                SwarmSpecializationRolePolicy.formationRadiusMultiplier(
                        SwarmSpecialization.OVERWATCH
                )
                        > SwarmSpecializationRolePolicy.formationRadiusMultiplier(
                        SwarmSpecialization.SUPPRESSOR
                )
        );
    }

    @Test
    void interceptorCanLeavePureFlankGeometryForPursuit() {
        assertEquals(
                SwarmRole.CHASER,
                SwarmSpecializationRolePolicy.role(
                        SwarmSpecialization.INTERCEPTOR,
                        SwarmRole.FLANK_LEFT
                )
        );
    }
}
