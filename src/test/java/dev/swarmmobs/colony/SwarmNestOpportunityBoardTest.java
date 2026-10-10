package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SwarmNestOpportunityBoardTest {
    private static final SwarmNestOpportunityBoard.Position HOME =
            new SwarmNestOpportunityBoard.Position(0,64,0);
    private static final SwarmNestColonyPolicy.Kind WOOD =
            SwarmNestColonyPolicy.Kind.TIMBER;
    private static final SwarmNestOpportunityBoard.Type BLOCK =
            SwarmNestOpportunityBoard.Type.BLOCK;
    private static final SwarmNestOpportunityBoard.Type ANIMAL =
            SwarmNestOpportunityBoard.Type.ANIMAL;

    private static SwarmNestOpportunityBoard.Opportunity reserve(
            SwarmNestOpportunityBoard board,UUID worker,
            SwarmNestOpportunityBoard.Type type,long tick) {
        return board.reserve(worker,HOME,type,tick,28,
                lead -> true,lead -> HOME.squaredDistance(lead.position()));
    }

    @Test void oneObservedSourceCannotBecomeTwoWorkerJobs() {
        var board=new SwarmNestOpportunityBoard();
        var source=new SwarmNestOpportunityBoard.Position(12,64,0);
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        assertTrue(board.publishBlock(source,WOOD,100,HOME));
        var job=reserve(board,a,BLOCK,101);
        assertNotNull(job);
        assertNull(reserve(board,b,BLOCK,102));
        assertEquals(1,board.activeWorkers(102));
        assertTrue(board.renew(job,a,110));
        assertFalse(board.renew(job,b,110));
        board.release(job,b);
        assertNull(reserve(board,b,BLOCK,111));
        board.release(job,a);
        assertNotNull(reserve(board,b,BLOCK,112));
        board.invalidate(job);
        assertEquals(0,board.size(112));
    }

    @Test void preyReportsAreDistinctFromBlocksAndRepeatedSightingsRefresh() {
        var board=new SwarmNestOpportunityBoard();
        var animal=UUID.randomUUID();
        var pos=new SwarmNestOpportunityBoard.Position(16,64,0);
        assertTrue(board.publishAnimal(animal,pos,100,HOME));
        assertTrue(board.publishAnimal(animal,new SwarmNestOpportunityBoard.Position(
                17,64,0),120,HOME));
        assertTrue(board.publishBlock(pos,WOOD,121,HOME));
        UUID worker=UUID.randomUUID();
        var report=reserve(board,worker,ANIMAL,121);
        assertNotNull(report);
        assertEquals(animal,report.animalId());
        assertEquals(2,report.sightings());
        assertEquals(120,report.lastSeen());
        assertEquals(2,board.size(121));
        board.invalidate(report);
        assertEquals(1,board.size(121));
    }

    @Test void reportsDoNotExistForeverAndCannotCrossNestRadius() {
        var board=new SwarmNestOpportunityBoard();
        var far=new SwarmNestOpportunityBoard.Position(29,64,0);
        assertFalse(board.publishBlock(far,WOOD,100,HOME));
        assertFalse(board.publishAnimal(null,HOME,100,HOME));
        assertFalse(board.publishBlock(HOME,SwarmNestColonyPolicy.Kind.NONE,100,HOME));
        var near=new SwarmNestOpportunityBoard.Position(6,64,0);
        assertTrue(board.publishBlock(near,WOOD,100,HOME));
        assertEquals(1,board.size(460));
        assertEquals(0,board.size(461));
        assertNull(reserve(board,UUID.randomUUID(),BLOCK,461));
    }

    @Test void manyConcurrentWorkersAreBoundedAndLeasesExpire() {
        var board=new SwarmNestOpportunityBoard();
        for(int i=0;i<SwarmNestOpportunityBoard.MAX_LEADS;i++) {
            assertTrue(board.publishBlock(
                    new SwarmNestOpportunityBoard.Position(i,64,0),WOOD,100,HOME));
        }
        assertEquals(SwarmNestOpportunityBoard.MAX_LEADS,board.size(100));
        for(int i=0;i<SwarmNestOpportunityBoard.MAX_ACTIVE_WORKERS;i++) {
            assertNotNull(reserve(board,UUID.randomUUID(),BLOCK,101));
        }
        assertNull(reserve(board,UUID.randomUUID(),BLOCK,101));
        assertEquals(SwarmNestOpportunityBoard.MAX_ACTIVE_WORKERS,board.activeWorkers(101));
        assertEquals(0,board.activeWorkers(202));
        assertNotNull(reserve(board,UUID.randomUUID(),BLOCK,203));
        assertEquals(SwarmNestOpportunityBoard.MAX_LEADS,board.size(203));
    }
}
