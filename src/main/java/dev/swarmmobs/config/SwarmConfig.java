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
    public static final ModConfigSpec.IntValue ROLE_HYSTERESIS_TICKS;
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
    public static final ModConfigSpec.DoubleValue SEARCH_SPEED_FACTOR;
    public static final ModConfigSpec.BooleanValue TARGET_PREDICTION_ENABLED;
    public static final ModConfigSpec.IntValue TARGET_PREDICTION_LEAD_TICKS;
    public static final ModConfigSpec.IntValue TARGET_PREDICTION_MAX_TICKS;
    public static final ModConfigSpec.DoubleValue TARGET_PREDICTION_MAX_DISTANCE;
    public static final ModConfigSpec.DoubleValue MOVE_SPEED;
    public static final ModConfigSpec.DoubleValue RELEASE_TO_VANILLA_DISTANCE;
    public static final ModConfigSpec.IntValue NAV_STUCK_WINDOW_TICKS;
    public static final ModConfigSpec.DoubleValue NAV_STUCK_MIN_PROGRESS;
    public static final ModConfigSpec.DoubleValue NAV_RECOVERY_LATERAL_DISTANCE;
    public static final ModConfigSpec.IntValue NAV_RECOVERY_DURATION_TICKS;
    public static final ModConfigSpec.BooleanValue NAV_OBSTACLE_AVOIDANCE_ENABLED;
    public static final ModConfigSpec.DoubleValue NAV_OBSTACLE_LOOKAHEAD;
    public static final ModConfigSpec.DoubleValue NAV_OBSTACLE_LATERAL_DISTANCE;
    public static final ModConfigSpec.IntValue NAV_OBSTACLE_HOLD_TICKS;
    public static final ModConfigSpec.DoubleValue NAV_OBSTACLE_ARRIVAL_TOLERANCE;
    public static final ModConfigSpec.BooleanValue NAV_WALKABILITY_ENABLED;
    public static final ModConfigSpec.IntValue NAV_MAX_PROBE_DROP_BLOCKS;
    public static final ModConfigSpec.DoubleValue NAV_LOCAL_PROGRESS_WEIGHT;
    public static final ModConfigSpec.DoubleValue NAV_LOCAL_LATERAL_PENALTY;
    public static final ModConfigSpec.DoubleValue NAV_LOCAL_CONGESTION_PENALTY;
    public static final ModConfigSpec.DoubleValue NAV_LOCAL_CONGESTION_RADIUS;
    public static final ModConfigSpec.IntValue NAV_PATH_EVIDENCE_BUDGET_PER_TICK;
    public static final ModConfigSpec.BooleanValue NAV_PATH_EVIDENCE_ENABLED;
    public static final ModConfigSpec.DoubleValue NAV_PATH_NODE_PENALTY;
    public static final ModConfigSpec.DoubleValue NAV_PATH_RESIDUAL_PENALTY;
    public static final ModConfigSpec.DoubleValue NAV_PATH_MAX_RESIDUAL_DISTANCE;

    public static final ModConfigSpec.BooleanValue ZOMBIE_ENGINEERING_ENABLED;
    public static final ModConfigSpec.DoubleValue ZOMBIE_ENGINEERING_MAX_BREAK_HARDNESS;
    public static final ModConfigSpec.IntValue ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS;
    public static final ModConfigSpec.DoubleValue ZOMBIE_ENGINEERING_TASK_RADIUS;
    public static final ModConfigSpec.IntValue ZOMBIE_ENGINEERING_TASK_TTL_TICKS;
    public static final ModConfigSpec.BooleanValue ZOMBIE_ENGINEERING_PATH_EVIDENCE_ENABLED;
    public static final ModConfigSpec.DoubleValue ZOMBIE_ENGINEERING_MATERIAL_HANDOFF_RADIUS;
    public static final ModConfigSpec.IntValue ZOMBIE_ENGINEERING_MAX_BRIDGE_SPAN;

    // New idle colony construction is opt-in to protect existing player worlds.
    public static final ModConfigSpec.BooleanValue NEST_CONSTRUCTION_ENABLED;
    public static final ModConfigSpec.BooleanValue NEST_LIFECYCLE_ENABLED;
    public static final ModConfigSpec.BooleanValue NEST_HAULING_ENABLED;
    public static final ModConfigSpec.BooleanValue NEST_BERRY_FORAGING_ENABLED;
    public static final ModConfigSpec.BooleanValue NEST_BLOCK_GATHER_ENABLED;
    public static final ModConfigSpec.BooleanValue NEST_ANIMAL_HUNT_ENABLED;
    public static final ModConfigSpec.BooleanValue NEST_PHEROMONES_ENABLED;
    public static final ModConfigSpec.BooleanValue NEST_PHEROMONE_EXPLORATION_ENABLED;
    public static final ModConfigSpec.IntValue NEST_GATHER_INTERVAL;
    public static final ModConfigSpec.IntValue NEST_BERRY_FORAGE_INTERVAL;
    public static final ModConfigSpec.IntValue NEST_HAUL_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue NEST_HAUL_MAX_STACK;
    public static final ModConfigSpec.IntValue NEST_HAUL_ATTEMPT_INTERVAL;
    public static final ModConfigSpec.BooleanValue NEST_VISIBLE_EXPANSION_ENABLED;
    public static final ModConfigSpec.IntValue NEST_MAX_POPULATION;
    public static final ModConfigSpec.BooleanValue NEST_ADAPTIVE_RECRUITMENT;
    public static final ModConfigSpec.DoubleValue NEST_WORKER_TARGET_SHARE;
    public static final ModConfigSpec.DoubleValue NEST_GUARD_TARGET_SHARE;
    public static final ModConfigSpec.DoubleValue NEST_RESPONSE_THRESHOLD;
    public static final ModConfigSpec.IntValue NEST_BUILD_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue NEST_MIN_GROUP_SIZE;
    public static final ModConfigSpec.BooleanValue DIVISION_OF_LABOR_ENABLED;
    public static final ModConfigSpec.IntValue SPECIALIZATION_MIN_HOLD_TICKS;
    public static final ModConfigSpec.DoubleValue SPECIALIZATION_EXPERIENCE_GAIN;
    public static final ModConfigSpec.DoubleValue SPECIALIZATION_EXPERIENCE_DECAY;

    public static final ModConfigSpec.BooleanValue SENSING_IMPERFECTION_ENABLED;
    public static final ModConfigSpec.DoubleValue SENSING_DROPOUT_RATE;
    public static final ModConfigSpec.DoubleValue SENSING_MAX_HORIZONTAL_NOISE;
    public static final ModConfigSpec.IntValue SENSING_EXPERIMENT_SEED;

    public static final ModConfigSpec.BooleanValue COMMUNICATION_ENABLED;
    public static final ModConfigSpec.DoubleValue COMMUNICATION_RADIUS;
    public static final ModConfigSpec.IntValue COMMUNICATION_LATENCY_TICKS;
    public static final ModConfigSpec.DoubleValue COMMUNICATION_PACKET_DROP_RATE;
    public static final ModConfigSpec.IntValue COMMUNICATION_EXPERIMENT_SEED;

    public static final ModConfigSpec.BooleanValue EXTERNAL_AI_ENABLED;
    public static final ModConfigSpec.BooleanValue EXTERNAL_AI_ACTIVE_ENABLED;
    public static final ModConfigSpec.IntValue EXTERNAL_AI_ACTIVE_TTL_TICKS;
    public static final ModConfigSpec.IntValue EXTERNAL_AI_ACTIVE_MIN_HOLD_TICKS;
    public static final ModConfigSpec.ConfigValue<String> OLLAMA_BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> OLLAMA_MODEL;
    public static final ModConfigSpec.IntValue OLLAMA_TIMEOUT_MS;
    public static final ModConfigSpec.ConfigValue<String> OLLAMA_KEEP_ALIVE;

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
                .comment("How long a different local slot candidate must remain stable before the agent switches formation lanes.")
                .defineInRange("formationSlotHysteresisTicks", 20, 0, 200);

        ROLE_HYSTERESIS_TICKS = BUILDER
                .comment("How long a different tactical responsibility must remain stable before role reassignment.")
                .defineInRange("roleHysteresisTicks", 12, 0, 200);

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

        SEARCH_SPEED_FACTOR = BUILDER
                .comment("Movement-speed factor used during SEARCH so expanding coverage does not slow with stale target confidence.")
                .defineInRange("searchSpeedFactor", 1.0, 0.25, 1.5);

        TARGET_PREDICTION_ENABLED = BUILDER
                .comment("Enable conservative short-horizon prediction from observed target velocity.")
                .define("targetPredictionEnabled", true);

        TARGET_PREDICTION_LEAD_TICKS = BUILDER
                .comment("Short lead horizon added to a fresh observation before confidence scaling.")
                .defineInRange("targetPredictionLeadTicks", 6, 0, 20);

        TARGET_PREDICTION_MAX_TICKS = BUILDER
                .comment("Hard upper bound on total prediction horizon.")
                .defineInRange("targetPredictionMaxTicks", 12, 0, 40);

        TARGET_PREDICTION_MAX_DISTANCE = BUILDER
                .comment("Hard upper bound, in blocks, on predicted position offset.")
                .defineInRange("targetPredictionMaxDistance", 3.5, 0.0, 12.0);

        MOVE_SPEED = BUILDER
                .comment("Navigation speed multiplier used for swarm repositioning.")
                .defineInRange("moveSpeed", 1.05, 0.2, 2.0);

        RELEASE_TO_VANILLA_DISTANCE = BUILDER
                .comment("Inside this distance, vanilla targeting/attack movement is allowed to dominate.")
                .defineInRange("releaseToVanillaDistance", 3.25, 1.0, 10.0);

        NAV_STUCK_WINDOW_TICKS = BUILDER
                .comment("Ticks with insufficient movement before local navigation recovery activates.")
                .defineInRange("navStuckWindowTicks", 24, 6, 200);

        NAV_STUCK_MIN_PROGRESS = BUILDER
                .comment("Minimum horizontal movement, in blocks, expected during the stuck-detection window.")
                .defineInRange("navStuckMinProgress", 0.75, 0.05, 4.0);

        NAV_RECOVERY_LATERAL_DISTANCE = BUILDER
                .comment("Side-step distance, in blocks, used for deterministic local recovery waypoints.")
                .defineInRange("navRecoveryLateralDistance", 2.0, 0.25, 6.0);

        NAV_RECOVERY_DURATION_TICKS = BUILDER
                .comment("How long an agent follows a temporary recovery waypoint before returning to the swarm plan.")
                .defineInRange("navRecoveryDurationTicks", 18, 3, 100);

        NAV_OBSTACLE_AVOIDANCE_ENABLED = BUILDER
                .comment("Enable short-range terrain probing before issuing swarm navigation destinations.")
                .define("navObstacleAvoidanceEnabled", true);

        NAV_OBSTACLE_LOOKAHEAD = BUILDER
                .comment("Forward probe and temporary detour distance, in blocks, for local obstacle avoidance.")
                .defineInRange("navObstacleLookahead", 1.5, 0.5, 4.0);

        NAV_OBSTACLE_LATERAL_DISTANCE = BUILDER
                .comment("Side probe and detour distance, in blocks, for local obstacle avoidance.")
                .defineInRange("navObstacleLateralDistance", 1.5, 0.5, 4.0);

        NAV_OBSTACLE_HOLD_TICKS = BUILDER
                .comment("Minimum time an obstacle detour is held to avoid left/right oscillation near obstacle edges.")
                .defineInRange("navObstacleHoldTicks", 12, 0, 100);

        NAV_OBSTACLE_ARRIVAL_TOLERANCE = BUILDER
                .comment("Distance from a temporary obstacle-detour waypoint that releases the detour early.")
                .defineInRange("navObstacleArrivalTolerance", 0.6, 0.1, 2.0);

        NAV_WALKABILITY_ENABLED = BUILDER
                .comment("Treat unsupported local probe points as blocked so detours avoid pits and ledges.")
                .define("navWalkabilityEnabled", true);

        NAV_MAX_PROBE_DROP_BLOCKS = BUILDER
                .comment("Maximum vertical drop, in blocks, accepted when checking local probe ground support.")
                .defineInRange("navMaxProbeDropBlocks", 1, 0, 4);

        NAV_LOCAL_PROGRESS_WEIGHT = BUILDER
                .comment("Weight rewarding short-horizon progress toward the swarm destination.")
                .defineInRange("navLocalProgressWeight", 1.0, 0.0, 4.0);

        NAV_LOCAL_LATERAL_PENALTY = BUILDER
                .comment("Penalty applied to excessive sideways detours.")
                .defineInRange("navLocalLateralPenalty", 0.20, 0.0, 4.0);

        NAV_LOCAL_CONGESTION_PENALTY = BUILDER
                .comment("Penalty applied to local planner candidates crowded by nearby swarm agents.")
                .defineInRange("navLocalCongestionPenalty", 0.75, 0.0, 4.0);

        NAV_LOCAL_CONGESTION_RADIUS = BUILDER
                .comment("Radius, in blocks, used to estimate crowding around local navigation candidates.")
                .defineInRange("navLocalCongestionRadius", 2.5, 0.5, 8.0);

        NAV_PATH_EVIDENCE_BUDGET_PER_TICK = BUILDER
                .comment("Maximum path-evidence reservations per server-level tick; complete local episodes defer when budget is exhausted.")
                .defineInRange("navPathEvidenceBudgetPerTick", 96, 8, 512);

        NAV_PATH_EVIDENCE_ENABLED = BUILDER
                .comment("Use Minecraft PathNavigation reachability and path cost as evidence for local detour candidates.")
                .define("navPathEvidenceEnabled", true);

        NAV_PATH_NODE_PENALTY = BUILDER
                .comment("Penalty per node in a candidate PathNavigation path.")
                .defineInRange("navPathNodePenalty", 0.05, 0.0, 2.0);

        NAV_PATH_RESIDUAL_PENALTY = BUILDER
                .comment("Penalty for PathNavigation residual distance when a path cannot end exactly on the target block.")
                .defineInRange("navPathResidualPenalty", 0.25, 0.0, 2.0);

        NAV_PATH_MAX_RESIDUAL_DISTANCE = BUILDER
                .comment("Maximum residual distance accepted for a non-null PathNavigation near-miss candidate.")
                .defineInRange("navPathMaxResidualDistance", 1.5, 0.0, 4.0);

        ZOMBIE_ENGINEERING_ENABLED = BUILDER
                .comment("Allow swarm Zombies on HARD difficulty to hand-break bounded soft obstacles and place salvaged blocks as simple bridge support.")
                .define("zombieEngineeringEnabled", true);

        ZOMBIE_ENGINEERING_MAX_BREAK_HARDNESS = BUILDER
                .comment("Maximum block hardness a bare-handed swarm Zombie may attempt to break. Unbreakable and block-entity blocks are always rejected.")
                .defineInRange("zombieEngineeringMaxBreakHardness", 2.0, 0.0, 10.0);

        ZOMBIE_ENGINEERING_MAX_CARRIED_BLOCKS = BUILDER
                .comment("Maximum salvaged placeable blocks carried by one Zombie engineer.")
                .defineInRange("zombieEngineeringMaxCarriedBlocks", 4, 0, 16);

        ZOMBIE_ENGINEERING_TASK_RADIUS = BUILDER
                .comment("Local radius in which Zombies may advertise and claim engineering tasks.")
                .defineInRange("zombieEngineeringTaskRadius", 8.0, 2.0, 24.0);

        ZOMBIE_ENGINEERING_TASK_TTL_TICKS = BUILDER
                .comment("Lifetime of one local engineering request before it must be republished.")
                .defineInRange("zombieEngineeringTaskTtlTicks", 40, 10, 400);

        ZOMBIE_ENGINEERING_PATH_EVIDENCE_ENABLED = BUILDER
                .comment("Require bounded PathNavigation evidence before a remote Zombie may claim an engineering task.")
                .define("zombieEngineeringPathEvidenceEnabled", true);

        ZOMBIE_ENGINEERING_MATERIAL_HANDOFF_RADIUS = BUILDER
                .comment("Maximum distance for a one-block engineering material handoff between nearby Zombies.")
                .defineInRange("zombieEngineeringMaterialHandoffRadius", 2.5, 0.5, 6.0);

        ZOMBIE_ENGINEERING_MAX_BRIDGE_SPAN = BUILDER
                .comment("Maximum consecutive unsupported blocks a local Zombie team may commit to bridging.")
                .defineInRange("zombieEngineeringMaxBridgeSpan", 4, 1, 8);

        BUILDER.pop();

        BUILDER.push("colonies");

        NEST_CONSTRUCTION_ENABLED = BUILDER
                .comment("EXPERIMENTAL: allow idle Zombie workers to place a persistent Nest Core on suitable natural soil; OFF by default to protect player worlds.")
                .define("nestConstructionEnabled", false);

        NEST_BUILD_INTERVAL_TICKS = BUILDER
                .comment("Minimum interval between idle nest-building site surveys for each worker (game ticks).")
                .defineInRange("nestBuildIntervalTicks", 200, 100, 1200);

        NEST_MIN_GROUP_SIZE = BUILDER
                .comment("Minimum local supported swarm mobs, including builder, required to found a nest.")
                .defineInRange("nestMinGroupSize", 3, 2, 16);

        NEST_LIFECYCLE_ENABLED = BUILDER
                .comment("EXPERIMENTAL: activate resource-fed nest lifecycle and capped colony spawning; OFF by default.")
                .define("nestLifecycleEnabled", false);

        NEST_HAULING_ENABLED = BUILDER
                .comment("EXPERIMENTAL: idle Zombies assigned to a loaded Nest Core carry actual nearby dropped resources to it. OFF by default.")
                .define("nestHaulingEnabled", false);

        NEST_BERRY_FORAGING_ENABLED = BUILDER
                .comment("EXPERIMENTAL: idle colony Zombies may pick renewable ripe sweet berries near a loaded nest and create real dropped berry items. OFF by default. Can touch player farms; use only in a designated test world. Requires nest lifecycle, hauling and mobGriefing.")
                .define("nestBerryForagingEnabled", false);

        NEST_BLOCK_GATHER_ENABLED = BUILDER
                .comment("EXPERIMENTAL: idle Zombie workers may mine actual soil/log blocks and harvest ripe crops for colony resources; affects player builds and farms intentionally when enabled. Requires lifecycle, hauling and mobGriefing.")
                .define("nestBlockGatherEnabled", false);

        NEST_ANIMAL_HUNT_ENABLED = BUILDER
                .comment("EXPERIMENTAL: idle Zombie workers may hunt adult farm animals to create vanilla physical food drops. Requires lifecycle, hauling and mobGriefing.")
                .define("nestAnimalHuntEnabled", false);

        NEST_GATHER_INTERVAL = BUILDER
                .comment("Ticks between bounded colony block or animal surveys per worker.")
                .defineInRange("nestGatherInterval", 160, 60, 800);

        NEST_PHEROMONES_ENABLED = BUILDER
                .comment("Use sparse decaying food, timber, soil and stop pheromone-like local cues in colony labor decisions. Only active with opt-in colony lifecycle and hauling.")
                .define("nestPheromonesEnabled", true);

        NEST_PHEROMONE_EXPLORATION_ENABLED = BUILDER
                .comment("Allow idle workers to make short, loaded-chunk-only exploratory hops toward locally sensed resource pheromones. Needs colony lifecycle, hauling, and pheromones.")
                .define("nestPheromoneExplorationEnabled", true);

        NEST_BERRY_FORAGE_INTERVAL = BUILDER
                .comment("Minimum per-worker ticks between bounded nearby ripe-berry foraging surveys.")
                .defineInRange("nestBerryForageInterval", 200, 120, 800);

        NEST_HAUL_SEARCH_RADIUS = BUILDER
                .comment("Maximum search radius in blocks from idle Zombie to a dropped resource; each worker is throttled and no chunks are loaded.")
                .defineInRange("nestHaulSearchRadius", 8, 4, 16);

        NEST_HAUL_MAX_STACK = BUILDER
                .comment("Maximum number of items in a dropped stack a Zombie may transport as one real entity.")
                .defineInRange("nestHaulMaxStack", 16, 1, 64);

        NEST_HAUL_ATTEMPT_INTERVAL = BUILDER
                .comment("Minimum ticks between a Zombie's idle resource-hauling surveys.")
                .defineInRange("nestHaulAttemptInterval", 100, 40, 400);

        NEST_VISIBLE_EXPANSION_ENABLED = BUILDER
                .comment("EXPERIMENTAL: physically place conservative soil/timber nest shell blocks when chambers expand. OFF by default. Requires mobGriefing; blocked sites defer upgrades.")
                .define("nestVisibleExpansionEnabled", false);

        NEST_MAX_POPULATION = BUILDER
                .comment("Maximum locally counted colony members before reproduction stops.")
                .defineInRange("nestMaxPopulation", 12, 3, 32);

        NEST_ADAPTIVE_RECRUITMENT = BUILDER
                .comment("If lifecycle is enabled, recruit based on local response-threshold workforce deficits rather than fixed species rotation.")
                .define("nestAdaptiveRecruitment", true);

        NEST_WORKER_TARGET_SHARE = BUILDER
                .comment("Desired fraction of local colony population made of Zombie workers (game model, not biological data).")
                .defineInRange("nestWorkerTargetShare", 0.40, 0.15, 0.65);

        NEST_GUARD_TARGET_SHARE = BUILDER
                .comment("Desired fraction of local colony population made of Skeleton guards; remaining slots are scouts/reserves.")
                .defineInRange("nestGuardTargetShare", 0.25, 0.10, 0.50);

        NEST_RESPONSE_THRESHOLD = BUILDER
                .comment("Response threshold theta in s^2/(s^2 + theta^2); higher values make recruiting less responsive to small deficits.")
                .defineInRange("nestResponseThreshold", 0.55, 0.10, 3.0);

        BUILDER.pop();

        BUILDER.push("divisionOfLabor");

        DIVISION_OF_LABOR_ENABLED = BUILDER
                .comment("Enable dynamic local task demand, bidding, and within-species specialization.")
                .define("enabled", true);

        SPECIALIZATION_MIN_HOLD_TICKS = BUILDER
                .comment("Minimum time a dynamic specialization is held before switching to another task.")
                .defineInRange("minHoldTicks", 30, 0, 400);

        SPECIALIZATION_EXPERIENCE_GAIN = BUILDER
                .comment("Experience reinforcement added to the active task on each planning update.")
                .defineInRange("experienceGain", 0.025, 0.0, 0.25);

        SPECIALIZATION_EXPERIENCE_DECAY = BUILDER
                .comment("Multiplicative experience retention per planning update; values below 1 slowly forget inactive specialization.")
                .defineInRange("experienceDecay", 0.995, 0.90, 1.0);

        BUILDER.pop();

        BUILDER.push("sensing");

        SENSING_IMPERFECTION_ENABLED = BUILDER
                .comment("Enable deterministic direct-sensing dropout and bounded horizontal position noise.")
                .define("imperfectionEnabled", false);

        SENSING_DROPOUT_RATE = BUILDER
                .comment("Deterministic probability that a valid direct observation is dropped.")
                .defineInRange("dropoutRate", 0.0, 0.0, 1.0);

        SENSING_MAX_HORIZONTAL_NOISE = BUILDER
                .comment("Maximum absolute X/Z observation error, in blocks, injected into accepted direct observations.")
                .defineInRange("maxHorizontalNoise", 0.0, 0.0, 8.0);

        SENSING_EXPERIMENT_SEED = BUILDER
                .comment("Seed for reproducible sensing fault/noise experiments.")
                .defineInRange("experimentSeed", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

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
                .comment("Enable local Ollama strategy requests. Shadow requests never change gameplay by themselves.")
                .define("enabled", false);

        EXTERNAL_AI_ACTIVE_ENABLED = BUILDER
                .comment("Allow explicitly requested, sanitized Ollama strategy decisions to influence bounded high-level swarm multipliers. OFF by default.")
                .define("activeEnabled", false);

        EXTERNAL_AI_ACTIVE_TTL_TICKS = BUILDER
                .comment("How long one accepted active AI strategy remains valid before deterministic baseline resumes.")
                .defineInRange("activeTtlTicks", 200, 20, 2400);

        EXTERNAL_AI_ACTIVE_MIN_HOLD_TICKS = BUILDER
                .comment("Minimum hold time before a different AI strategy mode may replace the current mode.")
                .defineInRange("activeMinHoldTicks", 60, 0, 1200);

        OLLAMA_BASE_URL = BUILDER
                .comment("Local Ollama base URL. Only loopback hosts are accepted.")
                .define("ollamaBaseUrl", "http://127.0.0.1:11434");

        OLLAMA_MODEL = BUILDER
                .comment("User-selected local Ollama model name. Empty means no model is selected.")
                .define("ollamaModel", "");

        OLLAMA_TIMEOUT_MS = BUILDER
                .comment("Maximum time for one asynchronous Ollama request.")
                .defineInRange("ollamaTimeoutMs", 5000, 250, 30000);

        OLLAMA_KEEP_ALIVE = BUILDER
                .comment("Ollama keep_alive value used for shadow strategy requests.")
                .define("ollamaKeepAlive", "5m");

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private SwarmConfig() {}
}
