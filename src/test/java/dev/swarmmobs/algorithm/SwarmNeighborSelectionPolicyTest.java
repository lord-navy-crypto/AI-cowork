package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmNeighborSelectionPolicyTest {
    private record Peer(String name, double distanceSquared) {}

    @Test
    void independentChannelsPreserveNearestPeersWithDifferentRadii() {
        List<Peer> candidates = List.of(
                new Peer("far", 36.0),
                new Peer("middle", 16.0),
                new Peer("near", 1.0),
                new Peer("close", 4.0),
                new Peer("beyond", 100.0)
        );

        var selected = SwarmNeighborSelectionPolicy.select(
                candidates,
                Peer::distanceSquared,
                3.0,
                8.0,
                3,
                true
        );

        assertEquals(List.of("near", "close"),
                selected.movement().stream().map(Peer::name).toList());
        assertEquals(List.of("near", "close", "middle"),
                selected.communication().stream().map(Peer::name).toList());
    }

    @Test
    void shorterCommunicationRadiusNeverStealsMovementSlots() {
        var selected = SwarmNeighborSelectionPolicy.select(
                List.of(
                        new Peer("farther", 25.0),
                        new Peer("near", 1.0),
                        new Peer("middle", 9.0)
                ),
                Peer::distanceSquared,
                6.0,
                2.0,
                3,
                true
        );
        assertEquals(List.of("near", "middle", "farther"),
                selected.movement().stream().map(Peer::name).toList());
        assertEquals(List.of("near"),
                selected.communication().stream().map(Peer::name).toList());
    }

    @Test
    void disabledCommunicationAndNeighborCapAreRespected() {
        List<Peer> candidates = List.of(
                new Peer("second", 4.0),
                new Peer("first", 1.0)
        );
        var selected = SwarmNeighborSelectionPolicy.select(
                candidates, Peer::distanceSquared, 4.0, 100.0, 1, false
        );
        assertEquals(List.of("first"), selected.movement().stream().map(Peer::name).toList());
        assertTrue(selected.communication().isEmpty());

        var none = SwarmNeighborSelectionPolicy.select(
                candidates, Peer::distanceSquared, 4.0, 100.0, 0, true
        );
        assertTrue(none.movement().isEmpty());
        assertTrue(none.communication().isEmpty());
    }

    @Test
    void ignoresInvalidDistancesAndIncludesRadiusBoundary() {
        var selected = SwarmNeighborSelectionPolicy.select(
                List.of(
                        new Peer("nan", Double.NaN),
                        new Peer("negative", -1.0),
                        new Peer("edge", 9.0),
                        new Peer("outside", 9.01)
                ),
                Peer::distanceSquared,
                3.0,
                3.0,
                5,
                true
        );
        assertEquals(List.of("edge"), selected.movement().stream().map(Peer::name).toList());
        assertEquals(List.of("edge"), selected.communication().stream().map(Peer::name).toList());
    }
}
