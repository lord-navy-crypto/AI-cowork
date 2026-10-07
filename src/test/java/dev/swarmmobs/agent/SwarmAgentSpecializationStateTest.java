package dev.swarmmobs.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmAgentSpecializationStateTest {

    @Test
    void minimumHoldPreventsRapidTaskThrashing() {
        SwarmAgentState state = new SwarmAgentState();

        assertEquals(
                SwarmSpecialization.VANGUARD,
                state.stabilizeSpecialization(
                        SwarmTaskType.BREACH,
                        SwarmSpecialization.VANGUARD,
                        100L,
                        30
                )
        );

        assertEquals(
                SwarmSpecialization.VANGUARD,
                state.stabilizeSpecialization(
                        SwarmTaskType.ENGINEERING,
                        SwarmSpecialization.ENGINEER,
                        110L,
                        30
                )
        );
        assertEquals(SwarmTaskType.BREACH, state.currentTask());

        assertEquals(
                SwarmSpecialization.ENGINEER,
                state.stabilizeSpecialization(
                        SwarmTaskType.ENGINEERING,
                        SwarmSpecialization.ENGINEER,
                        130L,
                        30
                )
        );
        assertEquals(1L, state.specializationSwitchCount());
    }

    @Test
    void disablingDynamicLaborClearsActiveTaskWithoutErasingExperience() {
        SwarmAgentState state = new SwarmAgentState();

        state.stabilizeSpecialization(
                SwarmTaskType.ENGINEERING,
                SwarmSpecialization.ENGINEER,
                100L,
                30
        );
        state.updateTaskExperience(SwarmTaskType.ENGINEERING, 0.40, 1.0);

        state.resetActiveSpecialization();

        assertEquals(SwarmTaskType.RESERVE, state.currentTask());
        assertEquals(SwarmSpecialization.RESERVE, state.specialization());
        assertEquals(Long.MIN_VALUE, state.specializationSinceTick());
        assertEquals(0.40, state.taskExperience(SwarmTaskType.ENGINEERING), 1e-9);

        assertEquals(
                SwarmSpecialization.FLANKER_LEFT,
                state.stabilizeSpecialization(
                        SwarmTaskType.FLANK,
                        SwarmSpecialization.FLANKER_LEFT,
                        101L,
                        30
                )
        );
        assertEquals(SwarmTaskType.FLANK, state.currentTask());
    }

    @Test
    void activeTaskExperienceReinforcesWhileInactiveExperienceDecays() {
        SwarmAgentState state = new SwarmAgentState();

        state.updateTaskExperience(SwarmTaskType.SEARCH, 0.20, 1.0);
        state.updateTaskExperience(SwarmTaskType.SEARCH, 0.20, 1.0);
        assertEquals(0.40, state.taskExperience(SwarmTaskType.SEARCH), 1e-9);

        state.updateTaskExperience(SwarmTaskType.FLANK, 0.10, 0.5);

        assertEquals(0.20, state.taskExperience(SwarmTaskType.SEARCH), 1e-9);
        assertEquals(0.10, state.taskExperience(SwarmTaskType.FLANK), 1e-9);
    }
}
