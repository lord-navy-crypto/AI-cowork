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
