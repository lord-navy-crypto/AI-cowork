package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmFireSupportLanePolicyTest {

    @Test
    void rangedSupportMovesOffTheBreacherIngressAxis() {
        var target = new SwarmCombatPlanner.Vec2(10.0, 0.0);
        var breacher = new SwarmCombatPlanner.Vec2(6.0, 0.0);
        var base = new SwarmCombatPlanner.Vec2(2.0, 0.0);

        var shifted = SwarmFireSupportLanePolicy.apply(
                base,
                target,
                new SwarmCombatPlanner.Vec2(1.0, 0.0),
                0,
                List.of(breacher)
        );

        double lateral = Math.abs(shifted.z() - target.z());
        assertTrue(
                lateral >= SwarmFireSupportLanePolicy.MIN_LATERAL_OFFSET
                        + SwarmFireSupportLanePolicy.BREACHER_CLEARANCE
                        - 1.0e-9
        );
    }

    @Test
    void adjacentSupportSlotsSplitAcrossOppositeCrossfireLanes() {
        var target = new SwarmCombatPlanner.Vec2(10.0, 0.0);
        var breacher = new SwarmCombatPlanner.Vec2(6.0, 0.0);
        var base = new SwarmCombatPlanner.Vec2(2.0, 0.0);

        var even = SwarmFireSupportLanePolicy.apply(
                base,
                target,
                new SwarmCombatPlanner.Vec2(1.0, 0.0),
                0,
                List.of(breacher)
        );
        var odd = SwarmFireSupportLanePolicy.apply(
                base,
                target,
                new SwarmCombatPlanner.Vec2(1.0, 0.0),
                1,
                List.of(breacher)
        );

        assertTrue((even.z() - target.z()) * (odd.z() - target.z()) < 0.0);
    }
}
