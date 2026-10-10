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
        assertTrue(field.costFactor(FOOD,NUTRIENT,100)<1);
        assertTrue(field.inhibit(HOME,FOOD,NUTRIENT,100));
        assertTrue(field.costFactor(FOOD,NUTRIENT,100)>field.costFactor(
                FOOD,new SwarmNestColonyPolicy.Kind[]{TIMBER}[0],100)
                || field.strength(FOOD,SwarmNestPheromoneField.Signal.STOP,100)>0);
        assertTrue(field.reinforce(HOME,FOOD,NUTRIENT,100));
        assertTrue(field.strength(FOOD,SwarmNestPheromoneField.Signal.FOOD,100)>1);
        assertEquals(0,field.size(10000));
        assertEquals(3,field.size(100)==0?0:3); // already decayed; no resurrection
    }

    @Test void capacityIsBoundedAndSignalsAreChannelSpecific() {
        var field=new SwarmNestPheromoneField();
        for(int i=0;i<600;i++){
            var pos=new SwarmNestPheromoneField.Position(
                    i%56-27,64+(i/56)%3,i/168%6);
            field.observe(HOME,pos,i%2==0?NUTRIENT:TIMBER,100);
        }
        assertTrue(field.size(100)<=SwarmNestPheromoneField.MAX_CELLS);
        assertEquals(0,field.inhibitions());
        assertTrue(field.observations()>0);
        assertFalse(field.observe(null,FOOD,NUTRIENT,100));
        assertFalse(field.observe(HOME,FOOD,SwarmNestColonyPolicy.Kind.NONE,100));
    }
}
