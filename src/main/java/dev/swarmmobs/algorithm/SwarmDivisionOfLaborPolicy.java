package dev.swarmmobs.algorithm;

import dev.swarmmobs.agent.SwarmAgentArchetype;
import dev.swarmmobs.agent.SwarmTaskType;

import java.util.UUID;
import java.util.function.ToDoubleFunction;

public final class SwarmDivisionOfLaborPolicy {

    public record Assignment(
            SwarmTaskType task,
            double utility,
            double demand,
            double responseThreshold
    ) {}

    public static Assignment choose(
            UUID entityId,
            SwarmAgentArchetype archetype,
            SwarmTaskType currentTask,
            ToDoubleFunction<SwarmTaskType> demandProvider,
            ToDoubleFunction<SwarmTaskType> experienceProvider,
            double normalizedDistance,
            boolean combatBusy
    ) {
        SwarmTaskType bestTask = SwarmTaskType.RESERVE;
        double bestUtility = Double.NEGATIVE_INFINITY;
        double bestDemand = 0.0;
        double bestThreshold = 0.0;

        for (SwarmTaskType task : SwarmTaskType.values()) {
            double demand = demandProvider == null ? 0.0 : demandProvider.applyAsDouble(task);
            double experience = experienceProvider == null ? 0.0 : experienceProvider.applyAsDouble(task);

            var candidate = new SwarmTaskBidPolicy.Candidate(
                    entityId,
                    archetype,
                    currentTask,
                    experience,
                    normalizedDistance,
                    combatBusy
            );
            var bid = SwarmTaskBidPolicy.bid(candidate, task, demand);
            double capability = SwarmTaskBidPolicy.capability(archetype, task);
            double threshold = SwarmTaskBidPolicy.responseThreshold(entityId, task, capability);

            if (bid.utility() > bestUtility
                    || (bid.utility() == bestUtility && task.ordinal() < bestTask.ordinal())) {
                bestTask = task;
                bestUtility = bid.utility();
                bestDemand = demand;
                bestThreshold = threshold;
            }
        }

        if (!Double.isFinite(bestUtility)) {
            return new Assignment(SwarmTaskType.RESERVE, 0.0, 0.0, 0.0);
        }

        return new Assignment(bestTask, bestUtility, bestDemand, bestThreshold);
    }

    private SwarmDivisionOfLaborPolicy() {}
}
