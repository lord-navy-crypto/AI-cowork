package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmColonyLaborFeedbackTest {
    private static final SwarmNestColonyPolicy.Kind FOOD =
            SwarmNestColonyPolicy.Kind.NUTRIENT;
    private static final SwarmNestColonyPolicy.Kind WOOD =
            SwarmNestColonyPolicy.Kind.TIMBER;
    @Test void onlyReturnedPhysicalCargoReinforcesOneResourceChannel() {
        var cues=new SwarmColonyLaborFeedback();
        assertEquals(1.0,cues.costFactor(FOOD,100),1e-9);
        cues.succeeded(FOOD,3,100);
        assertTrue(cues.reinforcement(FOOD,101)>0.0);
        assertTrue(cues.costFactor(FOOD,101)<1.0);
        assertEquals(1.0,cues.costFactor(WOOD,101),1e-9);
        assertEquals(1,cues.successes());
    }
    @Test void failureTemporarilyDiscouragesAndSuccessRehabilitatesWork() {
        var cues=new SwarmColonyLaborFeedback();
        cues.failed(WOOD,10);
        cues.failed(WOOD,10);
        assertTrue(cues.costFactor(WOOD,10)>1.0);
        double early=cues.inhibition(WOOD,10);
        double later=cues.inhibition(WOOD,610);
        assertTrue(later<early);
        assertEquals(early*0.5,later,1e-8);
        cues.succeeded(WOOD,2,610);
        assertTrue(cues.inhibition(WOOD,611)<later);
        assertEquals(2,cues.failures());
    }
    @Test void signalsAreFiniteBoundedAndResetTowardBaseline() {
        var cues=new SwarmColonyLaborFeedback();
        for(int i=0;i<100;i++) {
            cues.failed(FOOD,100);
            cues.succeeded(WOOD,16,100);
        }
        assertTrue(cues.inhibition(FOOD,100)<=3.0);
        assertTrue(cues.reinforcement(WOOD,100)<=4.0);
        assertTrue(cues.costFactor(FOOD,100)<=3.5);
        assertEquals(1.0,cues.costFactor(FOOD,60100),1e-6);
        assertEquals(1.0,cues.costFactor(WOOD,60100),1e-6);
    }
}
