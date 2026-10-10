package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmBehaviorMode;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SwarmNavigationEpisodePolicyTest {
    private static final UUID A = new UUID(5L, 1L);
    private static final UUID B = new UUID(5L, 2L);

    @Test void firstRunStartsFreshWithoutInventingAReset() {
        assertFalse(SwarmNavigationEpisodePolicy.changed(
                false,null,null,A,SwarmBehaviorMode.ENGAGE));
    }

    @Test void targetSwitchInvalidatesActiveDetoursImmediately() {
        assertTrue(SwarmNavigationEpisodePolicy.changed(
                true,A,SwarmBehaviorMode.ENGAGE,B,SwarmBehaviorMode.ENGAGE));
        assertTrue(SwarmNavigationEpisodePolicy.changed(
                true,A,SwarmBehaviorMode.SEARCH,null,SwarmBehaviorMode.SEARCH));
    }

    @Test void searchEngageHandoffIsNewNavigationEpisode() {
        assertTrue(SwarmNavigationEpisodePolicy.changed(
                true,A,SwarmBehaviorMode.SEARCH,A,SwarmBehaviorMode.ENGAGE));
        assertTrue(SwarmNavigationEpisodePolicy.changed(
                true,A,SwarmBehaviorMode.ENGAGE,A,SwarmBehaviorMode.SEARCH));
        assertFalse(SwarmNavigationEpisodePolicy.changed(
                true,A,SwarmBehaviorMode.SEARCH,A,SwarmBehaviorMode.SEARCH));
    }

    @Test void smallDirectTargetMotionKeepsExistingRecovery() {
        assertFalse(SwarmNavigationEpisodePolicy.abandonStaleRecovery(
                true,true,SwarmBehaviorMode.ENGAGE,0,0,4,4));
        assertFalse(SwarmNavigationEpisodePolicy.abandonStaleRecovery(
                false,true,SwarmBehaviorMode.ENGAGE,0,0,20,0));
    }

    @Test void largeReliableMovementEndsOldRecoveryEarly() {
        assertTrue(SwarmNavigationEpisodePolicy.abandonStaleRecovery(
                true,true,SwarmBehaviorMode.ENGAGE,0,0,6.1,0));
        assertFalse(SwarmNavigationEpisodePolicy.abandonStaleRecovery(
                true,true,SwarmBehaviorMode.ENGAGE,0,0,6,0));
    }

    @Test void searchNeverChurnsRecoveryBecauseSearchSectorsRotate() {
        assertFalse(SwarmNavigationEpisodePolicy.abandonStaleRecovery(
                true,true,SwarmBehaviorMode.SEARCH,0,0,40,0));
        assertFalse(SwarmNavigationEpisodePolicy.abandonStaleRecovery(
                true,false,SwarmBehaviorMode.ENGAGE,0,0,40,0));
    }

    @Test void invalidEvidenceCannotTriggerArtificialReset() {
        assertFalse(SwarmNavigationEpisodePolicy.abandonStaleRecovery(
                true,true,SwarmBehaviorMode.ENGAGE,
                Double.NaN,0,100,0));
        assertFalse(SwarmNavigationEpisodePolicy.abandonStaleRecovery(
                true,true,SwarmBehaviorMode.ENGAGE,
                0,0,Double.POSITIVE_INFINITY,0));
    }
}
