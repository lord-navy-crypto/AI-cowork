package dev.swarmmobs.experiment;

import dev.swarmmobs.agent.SwarmAgentProfiles;
import dev.swarmmobs.agent.SwarmAgentState;
import dev.swarmmobs.data.SwarmAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SwarmExperimentMetrics {

    public record Snapshot(
            boolean active,
            long elapsedTicks,
            int agentCount,
            long communicationAccepted,
            long communicationDelivered,
            long communicationDropped,
            long obstacleDetours,
            long recoveries,
            long recoveryPlanningAttempts,
            long recoveryPlanningFailures,
            long pathQueries,
            long roleReassignments,
            long searchEpisodesStarted,
            long searchEpisodesSucceeded,
            long searchEpisodesFailed,
            long searchReacquisitionTicksTotal,
            int activeSearchEpisodes
    ) {
        public double recoveryFailureRate() {
            return recoveryPlanningAttempts <= 0L
                    ? 0.0
                    : (double) recoveryPlanningFailures / recoveryPlanningAttempts;
        }

        public double communicationDropRate() {
            long total = communicationDelivered + communicationDropped;
            return total <= 0L ? 0.0 : (double) communicationDropped / total;
        }

        public double searchSuccessRate() {
            long completed = searchEpisodesSucceeded + searchEpisodesFailed;
            return completed <= 0L ? 0.0 : (double) searchEpisodesSucceeded / completed;
        }

        public double averageReacquisitionTicks() {
            return searchEpisodesSucceeded <= 0L
                    ? 0.0
                    : (double) searchReacquisitionTicksTotal / searchEpisodesSucceeded;
        }
    }

    private record Counters(
            long communicationAccepted,
            long communicationDelivered,
            long communicationDropped,
            long obstacleDetours,
            long recoveries,
            long recoveryPlanningAttempts,
            long recoveryPlanningFailures,
            long pathQueries,
            long roleReassignments,
            long searchEpisodesStarted,
            long searchEpisodesSucceeded,
            long searchEpisodesFailed,
            long searchReacquisitionTicksTotal
    ) {}

    private static final Map<UUID, Counters> BASELINES = new HashMap<>();
    private static boolean active;
    private static long startTick;

    public static void start(ServerLevel level) {
        BASELINES.clear();
        startTick = level.getGameTime();
        active = true;

        for (var entity : level.getAllEntities()) {
            if (entity instanceof PathfinderMob mob && SwarmAgentProfiles.isSupported(mob)) {
                BASELINES.put(mob.getUUID(), counters(mob.getData(SwarmAttachments.AGENT_STATE.get())));
            }
        }
    }

    public static void reset(ServerLevel level) {
        start(level);
    }

    public static Snapshot snapshot(ServerLevel level) {
        long accepted = 0L;
        long delivered = 0L;
        long dropped = 0L;
        long detours = 0L;
        long recoveries = 0L;
        long recoveryAttempts = 0L;
        long recoveryFailures = 0L;
        long pathQueries = 0L;
        long roleReassignments = 0L;
        long searchStarted = 0L;
        long searchSucceeded = 0L;
        long searchFailed = 0L;
        long reacquisitionTicks = 0L;
        int activeSearchEpisodes = 0;
        int agents = 0;

        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof PathfinderMob mob) || !SwarmAgentProfiles.isSupported(mob)) {
                continue;
            }

            agents++;
            SwarmAgentState state = mob.getData(SwarmAttachments.AGENT_STATE.get());
            Counters current = counters(state);
            Counters baseline = BASELINES.getOrDefault(mob.getUUID(), ZERO);

            accepted += delta(current.communicationAccepted(), baseline.communicationAccepted());
            delivered += delta(current.communicationDelivered(), baseline.communicationDelivered());
            dropped += delta(current.communicationDropped(), baseline.communicationDropped());
            detours += delta(current.obstacleDetours(), baseline.obstacleDetours());
            recoveries += delta(current.recoveries(), baseline.recoveries());
            recoveryAttempts += delta(current.recoveryPlanningAttempts(), baseline.recoveryPlanningAttempts());
            recoveryFailures += delta(current.recoveryPlanningFailures(), baseline.recoveryPlanningFailures());
            pathQueries += delta(current.pathQueries(), baseline.pathQueries());
            roleReassignments += delta(current.roleReassignments(), baseline.roleReassignments());
            searchStarted += delta(current.searchEpisodesStarted(), baseline.searchEpisodesStarted());
            searchSucceeded += delta(current.searchEpisodesSucceeded(), baseline.searchEpisodesSucceeded());
            searchFailed += delta(current.searchEpisodesFailed(), baseline.searchEpisodesFailed());
            reacquisitionTicks += delta(current.searchReacquisitionTicksTotal(), baseline.searchReacquisitionTicksTotal());
            if (state.searchEpisodeActive()) {
                activeSearchEpisodes++;
            }
        }

        long elapsed = active ? Math.max(0L, level.getGameTime() - startTick) : 0L;

        return new Snapshot(
                active,
                elapsed,
                agents,
                accepted,
                delivered,
                dropped,
                detours,
                recoveries,
                recoveryAttempts,
                recoveryFailures,
                pathQueries,
                roleReassignments,
                searchStarted,
                searchSucceeded,
                searchFailed,
                reacquisitionTicks,
                activeSearchEpisodes
        );
    }

    public static boolean active() {
        return active;
    }

    private static Counters counters(SwarmAgentState state) {
        return new Counters(
                state.communicationAcceptedMessages(),
                state.communicationDeliveredMessages(),
                state.communicationDroppedMessages(),
                state.obstacleDetourCount(),
                state.recoveryCount(),
                state.recoveryPlanningAttempts(),
                state.recoveryPlanningFailures(),
                state.plannerPathQueryCount(),
                state.roleReassignmentCount(),
                state.searchEpisodesStarted(),
                state.searchEpisodesSucceeded(),
                state.searchEpisodesFailed(),
                state.searchReacquisitionTicksTotal()
        );
    }

    private static long delta(long current, long baseline) {
        return Math.max(0L, current - baseline);
    }

    private static final Counters ZERO = new Counters(
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L
    );

    private SwarmExperimentMetrics() {}
}
