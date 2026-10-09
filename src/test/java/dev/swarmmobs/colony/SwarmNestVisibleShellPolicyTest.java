package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class SwarmNestVisibleShellPolicyTest {
    @Test
    void shellIsBoundedAndNonOverlappingWithinEachTier() {
        var placements = new HashSet<String>();
        for (int chamber = 0; chamber < SwarmNestArchitecturePolicy.MAX_CHAMBER_LEVEL; chamber++) {
            for (int slot = 0; slot < 2; slot++) {
                var piece = SwarmNestVisibleShellPolicy.piece(chamber, slot);
                assertTrue(Math.abs(piece.x()) <= 2 && Math.abs(piece.z()) <= 2);
                assertTrue(piece.y() == 0 || piece.y() == 1);
                assertEquals(slot == 0, piece.soil());
                assertTrue(placements.add(piece.x() + "," + piece.y() + "," + piece.z()),
                        "Every built module needs two distinct physical positions");
            }
        }
        assertEquals(14, placements.size());
    }

    @Test
    void upperTierMustRestOnCorrespondingExistingLowerTier() {
        for (int chamber = 4; chamber < 7; chamber++) {
            for (int slot = 0; slot < 2; slot++) {
                var upper = SwarmNestVisibleShellPolicy.piece(chamber, slot);
                var lower = SwarmNestVisibleShellPolicy.piece(chamber - 4, slot);
                assertEquals(lower.x(), upper.x());
                assertEquals(lower.z(), upper.z());
                assertEquals(0, lower.y());
                assertEquals(1, upper.y());
            }
        }
    }

    @Test
    void invalidChamberIndicesCannotCreateUnboundedWorldFootprints() {
        assertThrows(IllegalArgumentException.class, () -> SwarmNestVisibleShellPolicy.piece(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> SwarmNestVisibleShellPolicy.piece(7, 0));
        assertThrows(IllegalArgumentException.class, () -> SwarmNestVisibleShellPolicy.piece(0, -1));
        assertThrows(IllegalArgumentException.class, () -> SwarmNestVisibleShellPolicy.piece(0, 2));
    }
}
