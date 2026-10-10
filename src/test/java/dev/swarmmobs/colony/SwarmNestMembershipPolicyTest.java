package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmNestMembershipPolicyTest {
    @Test void unassignedAndCorrectlyAssignedNeighborsCanParticipate() {
        assertTrue(SwarmNestMembershipPolicy.eligible(false,true,false,false,false));
        assertTrue(SwarmNestMembershipPolicy.eligible(true,true,true,true,true));
    }

    @Test void liveForeignNestOwnsItsMembersEvenWhenTheyWalkNearAnotherCore() {
        assertFalse(SwarmNestMembershipPolicy.eligible(true,true,false,true,true));
        assertFalse(SwarmNestMembershipPolicy.eligible(true,true,false,false,false));
    }

    @Test void destroyedLoadedHomeCanBeReplacedButUnloadedHomeCannot() {
        assertTrue(SwarmNestMembershipPolicy.eligible(true,true,false,true,false));
        assertFalse(SwarmNestMembershipPolicy.eligible(true,true,false,false,false));
        assertTrue(SwarmNestMembershipPolicy.eligible(true,false,false,false,false));
    }
}
