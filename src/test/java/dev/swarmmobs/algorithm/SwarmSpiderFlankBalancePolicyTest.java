package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmSpiderFlankBalancePolicyTest {
    private static final UUID A = new UUID(0,1);
    private static final UUID B = new UUID(0,2);
    private static final UUID C = new UUID(0,3);

    @Test void twoSpidersOccupyOppositeGameFlanksWithoutLeader() {
        assertEquals(SwarmRole.FLANK_LEFT,
                SwarmSpiderFlankBalancePolicy.choose(A,SwarmRole.FLANK_RIGHT,
                        List.of(B),true));
        assertEquals(SwarmRole.FLANK_RIGHT,
                SwarmSpiderFlankBalancePolicy.choose(B,SwarmRole.FLANK_LEFT,
                        List.of(A),true));
    }

    @Test void threeSpidersAlternateLocalCoverageDeterministically() {
        assertEquals(SwarmRole.FLANK_LEFT,
                SwarmSpiderFlankBalancePolicy.choose(C,SwarmRole.FLANK_RIGHT,
                        List.of(B,A),true));
        assertEquals(SwarmRole.FLANK_RIGHT,
                SwarmSpiderFlankBalancePolicy.choose(B,SwarmRole.FLANK_LEFT,
                        List.of(C,A),true));
        assertEquals(SwarmRole.FLANK_LEFT,
                SwarmSpiderFlankBalancePolicy.choose(A,SwarmRole.FLANK_LEFT,
                        List.of(B,C),true));
    }

    @Test void lostSpiderRebalancesRemainingPairButSingletonKeepsOldRole() {
        assertEquals(SwarmRole.FLANK_RIGHT,
                SwarmSpiderFlankBalancePolicy.choose(B,SwarmRole.FLANK_LEFT,
                        List.of(A,C),true));
        assertEquals(SwarmRole.FLANK_LEFT,
                SwarmSpiderFlankBalancePolicy.choose(B,SwarmRole.FLANK_RIGHT,
                        List.of(C),true));
        assertEquals(SwarmRole.FLANK_RIGHT,
                SwarmSpiderFlankBalancePolicy.choose(B,SwarmRole.FLANK_RIGHT,
                        List.of(),true));
    }

    @Test void noDuplicatePhantomSpidersAndDisabledCoordinationPreservesRole() {
        assertEquals(SwarmRole.FLANK_RIGHT,
                SwarmSpiderFlankBalancePolicy.choose(B,SwarmRole.FLANK_RIGHT,
                        List.of(A,A,B),true));
        assertEquals(SwarmRole.FLANK_LEFT,
                SwarmSpiderFlankBalancePolicy.choose(B,SwarmRole.FLANK_LEFT,
                        List.of(A),false));
        assertEquals(SwarmRole.FLANK_LEFT,
                SwarmSpiderFlankBalancePolicy.choose(B,SwarmRole.FLANK_LEFT,
                        null,true));
    }
}
