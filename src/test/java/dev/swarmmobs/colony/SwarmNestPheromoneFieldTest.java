package dev.swarmmobs.colony;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmNestPheromoneFieldTest {
    private static final SwarmNestPheromoneField.Position HOME =
            new SwarmNestPheromoneField.Position(0,64,0);
    private static final SwarmNestPheromoneField.Position FOOD =
            new SwarmNestPheromoneField.Position(12,64,0);
    private static final SwarmNestColonyPolicy.Kind NUTRIENT =
            SwarmNestColonyPolicy.Kind.NUTRIENT;
    private static final SwarmNestColonyPolicy.Kind TIMBER =
            SwarmNestColonyPolicy.Kind.TIMBER;

    @Test void realLocalSourceHasEvaporatingSpatialSignal() {
        var field=new SwarmNestPheromoneField();
        assertTrue(field.observe(HOME,FOOD,NUTRIENT,100));
        var center=field.strength(FOOD,SwarmNestPheromoneField.Signal.FOOD,100);
        assertEquals(1.0,center,1e-9);
        assertEquals(0.5,field.strength(
                FOOD,SwarmNestPheromoneField.Signal.FOOD,500),1e-9);
        assertEquals(0,field.scent(new SwarmNestPheromoneField.Position(80,64,0),
                SwarmNestPheromoneField.Signal.FOOD,500),1e-9);
        assertTrue(field.scent(new SwarmNestPheromoneField.Position(8,64,0),
                SwarmNestPheromoneField.Signal.FOOD,100)>0);
        assertFalse(field.observe(HOME,new SwarmNestPheromoneField.Position(29,64,0),
                NUTRIENT,100));
        assertEquals(0,field.strength(FOOD,
                SwarmNestPheromoneField.Signal.TIMBER,100));
    }

    @Test void physicalSuccessReinforcesButStopInhibitsAndBothFade() {
        var field=new SwarmNestPheromoneField();
        assertEquals(1.0,field.costFactor(FOOD,NUTRIENT,100),1e-9);
        assertTrue(field.observe(HOME,FOOD,NUTRIENT,100));
        double recruited = field.costFactor(FOOD,NUTRIENT,100);
        assertTrue(recruited<1.0);
        assertTrue(field.inhibit(HOME,FOOD,NUTRIENT,100));
        assertTrue(field.costFactor(FOOD,NUTRIENT,100)>recruited);
        assertTrue(field.reinforce(HOME,FOOD,NUTRIENT,100));
        assertTrue(field.strength(FOOD,SwarmNestPheromoneField.Signal.FOOD,100)>1);
        assertEquals(0,field.size(10000));
    }

    @Test void capacityIsBoundedAndSignalsAreChannelSpecific() {
        var field=new SwarmNestPheromoneField();
        for(int i=0;i<600;i++){
            var pos=new SwarmNestPheromoneField.Position(
                    ((i%13)-6)*4, 64+(i/143)*4, (((i/13)%11)-5)*4);
            field.observe(HOME,pos,i%2==0?NUTRIENT:TIMBER,100);
        }
        assertEquals(SwarmNestPheromoneField.MAX_CELLS,field.size(100));
        assertEquals(0,field.inhibitions());
        assertTrue(field.observations()>0);
        assertFalse(field.observe(null,FOOD,NUTRIENT,100));
        assertFalse(field.observe(HOME,FOOD,SwarmNestColonyPolicy.Kind.NONE,100));
    }

    @Test void crowdingLimitedPhysicalReturnsActuallyDepositLessScent() {
        var sparse=new SwarmNestPheromoneField();
        var crowded=new SwarmNestPheromoneField();
        assertTrue(sparse.reinforce(HOME,FOOD,NUTRIENT,10,
                SwarmColonyEmergencePolicy.depositionMultiplier(0)));
        assertTrue(crowded.reinforce(HOME,FOOD,NUTRIENT,10,
                SwarmColonyEmergencePolicy.depositionMultiplier(5)));
        assertTrue(sparse.strength(FOOD,SwarmNestPheromoneField.Signal.FOOD,10)
                > crowded.strength(FOOD,SwarmNestPheromoneField.Signal.FOOD,10));
        assertFalse(crowded.reinforce(HOME,FOOD,NUTRIENT,10,Double.NaN));
        assertFalse(crowded.reinforce(HOME,FOOD,NUTRIENT,10,0));
    }
}
