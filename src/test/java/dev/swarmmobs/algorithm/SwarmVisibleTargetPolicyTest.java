package dev.swarmmobs.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SwarmVisibleTargetPolicyTest {
    private static final UUID A = new UUID(1L, 1L);
    private static final UUID B = new UUID(1L, 2L);
    private record VisiblePlayer(UUID id, double d2) {}

    private static VisiblePlayer pick(List<VisiblePlayer> visible, UUID previous) {
        return SwarmVisibleTargetPolicy.choose(
                visible, previous, VisiblePlayer::id, VisiblePlayer::d2);
    }

    @Test
    void retainsVisibleIncumbentWhenCompetitorIsOnlySlightlyCloser() {
        VisiblePlayer a = new VisiblePlayer(A, 100.0);
        VisiblePlayer b = new VisiblePlayer(B, 96.0);
        assertSame(a, pick(List.of(a, b), A));
    }

    @Test
    void switchesImmediatelyWhenAlternativeIsClearlyCloser() {
        VisiblePlayer a = new VisiblePlayer(A, 100.0);
        VisiblePlayer b = new VisiblePlayer(B, 64.0);
        assertSame(b, pick(List.of(a, b), A));
    }

    @Test
    void disappearedOrIneligibleIncumbentGetsNoSpecialPreference() {
        VisiblePlayer b = new VisiblePlayer(B, 64.0);
        assertSame(b, pick(List.of(b), A));
        assertNull(pick(List.of(), A));
    }

    @Test
    void noDirectIncumbentUsesNearestVisibleTarget() {
        VisiblePlayer a = new VisiblePlayer(A, 100.0);
        VisiblePlayer b = new VisiblePlayer(B, 81.0);
        assertSame(b, pick(List.of(a, b), null));
    }

    @Test
    void invalidDistancesAreNotUsedForTargetSelection() {
        VisiblePlayer invalid = new VisiblePlayer(A, Double.NaN);
        VisiblePlayer valid = new VisiblePlayer(B, 100.0);
        assertSame(valid, pick(List.of(invalid, valid), A));
        assertNull(pick(List.of(invalid), A));
    }
}
