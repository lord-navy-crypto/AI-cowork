package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmRole;
import dev.swarmmobs.algorithm.SwarmCombatPlanner.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmCombatPlannerTest {

    @Test
    void formationSlotIsStableForSameAgent() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        int first = SwarmCombatPlanner.formationSlot(id, 8);
        int second = SwarmCombatPlanner.formationSlot(id, 8);

        assertEquals(first, second);
        assertTrue(first >= 0 && first < 8);
    }

    @Test
    void slotRolesCycleAcrossFourBehaviorClasses() {
        assertEquals(SwarmRole.CHASER, SwarmCombatPlanner.roleForSlot(0));
        assertEquals(SwarmRole.FLANK_LEFT, SwarmCombatPlanner.roleForSlot(1));
        assertEquals(SwarmRole.FLANK_RIGHT, SwarmCombatPlanner.roleForSlot(2));
        assertEquals(SwarmRole.REAR_PRESSURE, SwarmCombatPlanner.roleForSlot(3));
        assertEquals(SwarmRole.CHASER, SwarmCombatPlanner.roleForSlot(4));
    }

    @Test
    void separationPushesAwayFromCloseNeighbor() {
        Vec2 self = new Vec2(0.0, 0.0);
        Vec2 closeRight = new Vec2(1.0, 0.0);

        Vec2 result = SwarmCombatPlanner.separation(self, List.of(closeRight), 2.0);

        assertTrue(result.x() < 0.0);
        assertEquals(0.0, result.z(), 1.0e-9);
    }

    @Test
    void distantNeighborsDoNotCreateSeparation() {
        Vec2 self = new Vec2(0.0, 0.0);
        Vec2 farRight = new Vec2(5.0, 0.0);

        Vec2 result = SwarmCombatPlanner.separation(self, List.of(farRight), 2.0);

        assertEquals(0.0, result.length(), 1.0e-9);
    }

    @Test
    void cohesionPointsTowardNeighborCentroid() {
        Vec2 self = new Vec2(0.0, 0.0);
        Vec2 result = SwarmCombatPlanner.cohesion(
                self,
                List.of(new Vec2(2.0, 0.0), new Vec2(4.0, 0.0))
        );

        assertTrue(result.x() > 0.99);
        assertEquals(0.0, result.z(), 1.0e-9);
    }

    @Test
    void alignmentSteersTowardAverageNeighborMotion() {
        Vec2 alignment = SwarmCombatPlanner.alignment(
                new Vec2(0.0, 0.0),
                List.of(
                        new Vec2(1.0, 0.0),
                        new Vec2(2.0, 0.0)
                )
        );

        assertTrue(alignment.x() > 0.99);
        assertEquals(0.0, alignment.z(), 1.0e-9);
    }

    @Test
    void steeringCorrectionIsHardCapped() {
        Vec2 capped = SwarmCombatPlanner.clampLength(new Vec2(6.0, 8.0), 3.0);

        assertEquals(3.0, capped.length(), 1.0e-9);
        assertEquals(1.8, capped.x(), 1.0e-9);
        assertEquals(2.4, capped.z(), 1.0e-9);
    }

    @Test
    void alignmentContributesToMotionAwarePlan() {
        SwarmCombatPlanner.Plan baseline = SwarmCombatPlanner.planForSlotWithMotion(
                0,
                new Vec2(0.0, 0.0),
                new Vec2(0.0, 0.0),
                new Vec2(10.0, 0.0),
                new Vec2(1.0, 0.0),
                List.of(),
                List.of(),
                8,
                4.5,
                2.4,
                0.0,
                0.0,
                1.0,
                3.0
        );

        SwarmCombatPlanner.Plan aligned = SwarmCombatPlanner.planForSlotWithMotion(
                0,
                new Vec2(0.0, 0.0),
                new Vec2(0.0, 0.0),
                new Vec2(10.0, 0.0),
                new Vec2(1.0, 0.0),
                List.of(),
                List.of(new Vec2(0.0, 1.0)),
                8,
                4.5,
                2.4,
                0.0,
                0.0,
                1.0,
                3.0
        );

        assertEquals(0.0, baseline.alignmentMagnitude(), 1.0e-9);
        assertTrue(aligned.alignmentMagnitude() > 0.99);
        assertNotEquals(baseline.destination(), aligned.destination());
    }

    @Test
    void roleGeometryMatchesRoleNames() {
        Vec2 target = new Vec2(10.0, 10.0);
        Vec2 forward = new Vec2(0.0, 1.0);

        SwarmCombatPlanner.Plan left = SwarmCombatPlanner.plan(
                new UUID(0L, 1L),
                new Vec2(0.0, 0.0),
                target,
                forward,
                List.of(),
                8,
                4.0,
                2.0,
                0.0,
                0.0
        );
        SwarmCombatPlanner.Plan right = SwarmCombatPlanner.plan(
                new UUID(0L, 2L),
                new Vec2(0.0, 0.0),
                target,
                forward,
                List.of(),
                8,
                4.0,
                2.0,
                0.0,
                0.0
        );
        SwarmCombatPlanner.Plan rear = SwarmCombatPlanner.plan(
                new UUID(0L, 3L),
                new Vec2(0.0, 0.0),
                target,
                forward,
                List.of(),
                8,
                4.0,
                2.0,
                0.0,
                0.0
        );

        assertEquals(SwarmRole.FLANK_LEFT, left.role());
        assertTrue(left.destination().x() < target.x());

        assertEquals(SwarmRole.FLANK_RIGHT, right.role());
        assertTrue(right.destination().x() > target.x());

        assertEquals(SwarmRole.REAR_PRESSURE, rear.role());
        assertTrue(rear.destination().z() < target.z());
    }

    @Test
    void nonChaserReceivesOffsetApproachPoint() {
        UUID id = findUuidForRole(SwarmRole.FLANK_LEFT);
        Vec2 target = new Vec2(10.0, 10.0);

        SwarmCombatPlanner.Plan plan = SwarmCombatPlanner.plan(
                id,
                new Vec2(0.0, 0.0),
                target,
                new Vec2(0.0, 1.0),
                List.of(),
                8,
                4.0,
                2.0,
                0.0,
                0.0
        );

        assertNotEquals(target, plan.destination());
        assertEquals(SwarmRole.FLANK_LEFT, plan.role());
    }

    private static UUID findUuidForRole(SwarmRole role) {
        for (long i = 1; i < 1000; i++) {
            UUID id = new UUID(0L, i);
            if (SwarmCombatPlanner.roleForSlot(SwarmCombatPlanner.formationSlot(id, 8)) == role) {
                return id;
            }
        }
        throw new AssertionError("Could not produce UUID for role " + role);
    }
}
