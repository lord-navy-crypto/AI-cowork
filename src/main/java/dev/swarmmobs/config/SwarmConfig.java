package dev.swarmmobs.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SwarmConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue PLAN_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue NEIGHBOR_RADIUS;
    public static final ModConfigSpec.IntValue MAX_NEIGHBORS;
    public static final ModConfigSpec.DoubleValue TARGET_RADIUS;
    public static final ModConfigSpec.IntValue TARGET_MEMORY_TICKS;
    public static final ModConfigSpec.IntValue FORMATION_SLOTS;
    public static final ModConfigSpec.IntValue FORMATION_SLOT_HYSTERESIS_TICKS;
    public static final ModConfigSpec.DoubleValue FORMATION_RADIUS;
    public static final ModConfigSpec.DoubleValue SEPARATION_RADIUS;
    public static final ModConfigSpec.DoubleValue SEPARATION_WEIGHT;
    public static final ModConfigSpec.DoubleValue COHESION_WEIGHT;
    public static final ModConfigSpec.DoubleValue ALIGNMENT_WEIGHT;
    public static final ModConfigSpec.DoubleValue MAX_STEERING_CORRECTION;
    public static final ModConfigSpec.DoubleValue STALE_TARGET_MIN_SPEED_FACTOR;
    public static final ModConfigSpec.DoubleValue SEARCH_CONFIDENCE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue SEARCH_MIN_RADIUS;
    public static final ModConfigSpec.DoubleValue SEARCH_MAX_RADIUS;
    public static final ModConfigSpec.IntValue SEARCH_PHASE_TICKS;
    public static final ModConfigSpec.DoubleValue SEARCH_ARRIVAL_TOLERANCE;
    public static final ModConfigSpec.IntValue SEARCH_PREDICTION_MAX_TICKS;
    public static final ModConfigSpec.DoubleValue SEARCH_PREDICTION_MAX_DISTANCE;
    public static final ModConfigSpec.DoubleValue MOVE_SPEED;
    public static final ModConfigSpec.DoubleValue RELEASE_TO_VANILLA_DISTANCE;

    public static final ModConfigSpec.BooleanValue COMMUNICATION_ENABLED;
    public static final ModConfigSpec.DoubleValue COMMUNICATION_RADIUS;
    public static final ModConfigSpec.IntValue COMMUNICATION_LATENCY_TICKS;
    public static final ModConfigSpec.DoubleValue COMMUNICATION_PACKET_DROP_RATE;
    public static final ModConfigSpec.IntValue COMMUNICATION_EXPERIMENT_SEED;

    public static final ModConfigSpec.BooleanValue EXTERNAL_AI_ENABLED;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("swarm");

        ENABLED = BUILDER
                .comment("Master switch for the algorithmic swarm layer.")
                .define("enabled", true);

        PLAN_INTERVAL_TICKS = BUILDER
                .comment("How often each swarm mob replans. 20 ticks = 1 second.")
                .defineInRange("planIntervalTicks", 6, 1, 40);

        NEIGHBOR_RADIUS = BUILDER
                .comment("Local sensing radius used to discover nearby swarm peers.")
                .defineInRange("neighborRadius", 16.0, 2.0, 64.0);

        MAX_NEIGHBORS = BUILDER
                .comment("Maximum local peers considered by one planning update.")
                .defineInRange("maxNeighbors", 12, 1, 64);

        TARGET_RADIUS = BUILDER
                .comment("Radius for direct player observation.")
                .defineInRange("targetRadius", 32.0, 4.0, 96.0);

        TARGET_MEMORY_TICKS = BUILDER
                .comment("How long shared target memory remains usable without a new observation.")
                .defineInRange("targetMemoryTicks", 100, 1, 1200);

        FORMATION_SLOTS = BUILDER
                .comment("Number of deterministic approach slots around a target.")
                .defineInRange("formationSlots", 8, 4, 32);

        FORMATION_SLOT_HYSTERESIS_TICKS = BUILDER
                .comment("How long a different local slot candidate must remain stable before the agent switches roles/lanes.")
                .defineInRange("formationSlotHysteresisTicks", 20, 0, 200);

        FORMATION_RADIUS = BUILDER
                .comment("Nominal ring radius around the shared target.")
                .defineInRange("formationRadius", 4.5, 1.5, 16.0);

        SEPARATION_RADIUS = BUILDER
                .comment("Peers closer than this contribute a repulsive steering term.")
                .defineInRange("separationRadius", 2.4, 0.5, 8.0);

        SEPARATION_WEIGHT = BUILDER
                .comment("Strength, in blocks, of local anti-crowding steering.")
                .defineInRange("separationWeight", 2.0, 0.0, 8.0);

        COHESION_WEIGHT = BUILDER
                .comment("Strength, in blocks, pulling isolated mobs toward their local neighbor centroid.")
                .defineInRange("cohesionWeight", 0.35, 0.0, 4.0);

        ALIGNMENT_WEIGHT = BUILDER
                .comment("Strength of steering toward the average movement direction of local peers.")
                .defineInRange("alignmentWeight", 0.45, 0.0, 4.0);

        MAX_STEERING_CORRECTION = BUILDER
                .comment("Maximum combined separation/cohesion/alignment correction in blocks.")
                .defineInRange("maxSteeringCorrection", 3.0, 0.25, 12.0);

        STALE_TARGET_MIN_SPEED_FACTOR = BUILDER
                .comment("Minimum fraction of moveSpeed used when target information is almost expired.")
                .defineInRange("staleTargetMinSpeedFactor", 0.55, 0.1, 1.0);

        SEARCH_CONFIDENCE_THRESHOLD = BUILDER
                .comment("Indirect target confidence below this value switches the swarm from ENGAGE to SEARCH.")
                .defineInRange("searchConfidenceThreshold", 0.45, 0.05, 0.95);

        SEARCH_MIN_RADIUS = BUILDER
                .comment("Minimum decentralized search radius around the last-known target position.")
                .defineInRange("searchMinRadius", 2.0, 0.5, 16.0);

        SEARCH_MAX_RADIUS = BUILDER
                .comment("Maximum decentralized search radius as target confidence approaches zero.")
                .defineInRange("searchMaxRadius", 10.0, 2.0, 32.0);

        SEARCH_PHASE_TICKS = BUILDER
                .comment("How often search sectors rotate around the last-known target. 20 ticks = 1 second.")
                .defineInRange("searchPhaseTicks", 20, 5, 200);

        SEARCH_ARRIVAL_TOLERANCE = BUILDER
                .comment("Distance from a search destination at which the swarm movement goal yields.")
                .defineInRange("searchArrivalTolerance", 1.25, 0.5, 4.0);

        SEARCH_PREDICTION_MAX_TICKS = BUILDER
                .comment("Maximum dead-reckoning horizon from the last observed target velocity.")
                .defineInRange("searchPredictionMaxTicks", 30, 0, 100);

        SEARCH_PREDICTION_MAX_DISTANCE = BUILDER
                .comment("Maximum distance, in blocks, that dead-reckoning may shift the search anchor.")
                .defineInRange("searchPredictionMaxDistance", 6.0, 0.0, 24.0);

        MOVE_SPEED = BUILDER
                .comment("Navigation speed multiplier used for swarm repositioning.")
                .defineInRange("moveSpeed", 1.05, 0.2, 2.0);

        RELEASE_TO_VANILLA_DISTANCE = BUILDER
                .comment("Inside this distance, vanilla targeting/attack movement is allowed to dominate.")
                .defineInRange("releaseToVanillaDistance", 3.25, 1.0, 10.0);

        BUILDER.pop();

        BUILDER.push("communication");

        COMMUNICATION_ENABLED = BUILDER
                .comment("Enable explicit neighbor-to-neighbor target-message communication.")
                .define("enabled", true);

        COMMUNICATION_RADIUS = BUILDER
                .comment("Maximum distance, in blocks, for one swarm agent to send target information to another.")
                .defineInRange("radius", 16.0, 1.0, 96.0);

        COMMUNICATION_LATENCY_TICKS = BUILDER
                .comment("One-way delivery latency applied to swarm target messages.")
                .defineInRange("latencyTicks", 0, 0, 400);

        COMMUNICATION_PACKET_DROP_RATE = BUILDER
                .comment("Deterministic packet-drop probability from 0.0 to 1.0.")
                .defineInRange("packetDropRate", 0.0, 0.0, 1.0);

        COMMUNICATION_EXPERIMENT_SEED = BUILDER
                .comment("Seed mixed into deterministic packet-drop decisions for reproducible experiments.")
                .defineInRange("experimentSeed", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

        BUILDER.pop();

        BUILDER.push("externalAi");
        EXTERNAL_AI_ENABLED = BUILDER
                .comment("Reserved switch for a future external AI/LLM decision provider. v0.1 ignores it.")
                .define("enabled", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private SwarmConfig() {}
}
