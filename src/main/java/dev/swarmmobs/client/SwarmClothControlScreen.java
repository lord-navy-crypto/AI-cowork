package dev.swarmmobs.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Scrollable client-side presentation for the server-authoritative Swarm Command Center.
 *
 * Cloth Config owns layout, scrolling, categories, validation widgets and Save/Cancel
 * behavior. Values are still applied through the existing Swarm network actions so the
 * server remains authoritative.
 */
public final class SwarmClothControlScreen {
    private SwarmClothControlScreen() {}

    public static Screen create(Screen parent, String snapshot) {
        Map<String, String> values = parseSnapshot(snapshot);

        boolean canEdit = bool(values, "canEdit");

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Swarm Mobs Command Center"))
                .setEditable(canEdit)
                .setDoesConfirmSave(true)
                .setAlwaysShowTabs(true)
                .setShouldListSmoothScroll(true)
                .setShouldTabsSmoothScroll(true);

        ConfigEntryBuilder entries = builder.entryBuilder();

        buildOverview(builder.getOrCreateCategory(Component.literal("Overview")), entries, values);
        buildAi(builder.getOrCreateCategory(Component.literal("AI")), entries, values);
        buildEngineering(builder.getOrCreateCategory(Component.literal("Engineering")), entries, values);
        buildCoordination(builder.getOrCreateCategory(Component.literal("Coordination & Labor")), entries, values);
        buildSensing(builder.getOrCreateCategory(Component.literal("Sensing & Communication")), entries, values);
        buildSearch(builder.getOrCreateCategory(Component.literal("Search & Prediction")), entries, values);
        buildNavigation(builder.getOrCreateCategory(Component.literal("Navigation")), entries, values);
        buildExperiment(builder.getOrCreateCategory(Component.literal("Experiment")), entries, values);

        return builder.build();
    }

    private static void buildOverview(
            ConfigCategory category,
            ConfigEntryBuilder entries,
            Map<String, String> values
    ) {
        status(category, entries, bool(values, "canEdit")
                ? "Server-authoritative live controls. Save writes the SERVER configuration. Reopen to verify persisted values."
                : "Read-only view. Server operator permission is required to change Swarm settings.");
        status(category, entries,
                "Agents: " + integer(values, "liveAgents")
                        + "  |  Zombies " + integer(values, "liveZombies")
                        + "  Skeletons " + integer(values, "liveSkeletons")
                        + "  Spiders " + integer(values, "liveSpiders")
                        + "  Creepers " + integer(values, "liveCreepers"));

        toggle(category, entries, values,
                "Swarm master",
                "master",
                "toggle_master",
                true,
                "Master switch for the deterministic swarm layer.");

        // Keep AI at the very top of the command center instead of hiding it in a
        // deep tab. This is deliberately duplicated in the AI category for detail.
        toggle(category, entries, values,
                "Local AI service (Ollama)",
                "aiEnabled",
                "ai_toggle",
                false,
                "Enable or disable the local Ollama strategy service.");

        toggle(category, entries, values,
                "Apply bounded AI strategy",
                "aiActiveEnabled",
                "ai_active_toggle",
                false,
                "When enabled, AI may bias bounded high-level strategy demand; it does not directly control per-tick movement or attacks.");

        status(category, entries,
                "AI: " + (bool(values, "aiActive")
                        ? "ACTIVE " + text(values, "aiActiveMode", "BASELINE")
                        : (bool(values, "aiEnabled") ? "SHADOW READY" : "OFF")));

        boolean fullColonyTest =
                bool(values, "nestEnabled")
                && bool(values, "nestLifecycleEnabled")
                && bool(values, "nestHaulingEnabled")
                && bool(values, "nestBlockGatherEnabled")
                && bool(values, "nestAnimalHuntEnabled")
                && bool(values, "nestBerryForagingEnabled")
                && bool(values, "nestCropReplantEnabled")
                && bool(values, "nestVisibleExpansionEnabled");
        status(category, entries, "Colony experimental profile: "
                + (fullColonyTest ? "ALL ACTIVE" : "PARTIALLY ENABLED / OFF")
                + " (server-verified snapshot)");
        status(category, entries,
                "CAUTION: colony harvesting may alter terrain, trees, crops and animals. Use a disposable world.");
        action(category, entries, "Enable ALL colony experiments (test world)",
                "playtest_enable", "Tick and Save to enable founding, gathering, hauling, hunting, "
                        + "breeding and visible expansion; also enables relevant world gamerules.");
        action(category, entries, "Disable destructive colony experiments",
                "playtest_disable", "Tick and Save to disable jobs, breeding and shell placement. "
                        + "Previous world gamerules are not reset.");

        status(category, entries,
                "Live tactics: SWEEP " + integer(values, "tacticSweep")
                        + "  SURROUND " + integer(values, "tacticSurround")
                        + "  STANDARD " + integer(values, "tacticStandard")
                        + "  SEARCH " + integer(values, "tacticSearch"));
        status(category, entries,
                "Skeleton game spacing: " + integer(values, "skeletonSpacing")
                        + " actively repositioning / " + integer(values, "skeletonSpacingEpisodes")
                        + " spacing episodes / " + integer(values, "skeletonWaypointHolds")
                        + " verified waypoint holds");
        status(category, entries,
                "AI movement fallback (real): Skeleton " + integer(values, "skeletonMoveFallbacks")
                        + " / Zombie flank " + integer(values, "zombieFlankFallbacks"));
        status(category, entries,
                "Measured stalled waypoints: Skeleton " + integer(values, "skeletonNoProgress")
                        + " / Zombie " + integer(values, "zombieNoProgress"));
        status(category, entries,
                "Rejected Minecraft paths: " + integer(values, "gamePathRejects")
                        + " total / Skeleton optional " + integer(values, "skeletonPathRejects")
                        + " / Zombie optional " + integer(values, "zombiePathRejects"));
        status(category, entries,
                "Recent blocked route memories: Skeleton " + integer(values, "skeletonFailedSites")
                        + " / Zombie " + integer(values, "zombieFailedSites")
                        + " (up to 3 per NPC)");
        status(category, entries,
                "Vacant flank coverage: " + integer(values, "activeFlankFillers")
                        + " active Zombies / " + integer(values, "flankFillEpisodes")
                        + " replacement episodes (server observed)");
        status(category, entries,
                "Navigation stale-route resets: " + integer(values, "staleRouteResets"));
        status(category, entries,
                "SEARCH rejoining: " + integer(values, "searchRallying")
                        + " active agents / " + integer(values, "searchRallyEpisodes")
                        + " local regrouping episodes");
        status(category, entries,
                "Active crowd lane diversions: " + integer(values, "laneDiverted")
                        + "  / total planning samples " + integer(values, "laneDiversionSamples")
                        + "  / blocked fallback " + integer(values, "laneBlockedFallbacks"));
        status(category, entries,
                "These are live server measurements. Move in Survival to test SWEEP; "
                        + "stand still near a same-target group to test SURROUND.");

        status(category, entries,
                "Tasks: ENG " + integer(values, "taskEngineering")
                        + "  MAT " + integer(values, "taskMaterial")
                        + "  FLANK " + integer(values, "taskFlank")
                        + "  RNG " + integer(values, "taskRanged")
                        + "  BREACH " + integer(values, "taskBreach")
                        + "  SEARCH " + integer(values, "taskSearch"));

        action(category, entries,
                "Run AI strategy recommendation once",
                "ai_shadow",
                "Enable this checkbox and Save to request one server-side AI strategy recommendation.");

        action(category, entries,
                "Restore ALL known-good baselines",
                "baseline_all",
                "Enable this checkbox and Save to restore deterministic baseline values.");
    }

    private static void buildAi(
            ConfigCategory category,
            ConfigEntryBuilder entries,
            Map<String, String> values
    ) {
        status(category, entries,
                "Quick AI switches are intentionally kept on Overview to avoid duplicate toggle actions.");
        status(category, entries,
                "Local AI service: " + (bool(values, "aiEnabled") ? "ON" : "OFF")
                        + "  | bounded active strategy: "
                        + (bool(values, "aiActiveEnabled") ? "ON" : "OFF"));

        status(category, entries, "Model: " + text(values, "aiModel", "(not configured)"));
        status(category, entries,
                "Provider: " + text(values, "aiProvider", "none")
                        + "  |  status: " + text(values, "aiStatus", "IDLE")
                        + "  |  mode: " + text(values, "aiMode", "BASELINE"));
        status(category, entries,
                "Latency: " + integer(values, "aiLatencyMs") + " ms"
                        + "  |  successes " + integer(values, "aiSuccessCount")
                        + "  fallbacks " + integer(values, "aiFallbackCount")
                        + "  errors " + integer(values, "aiErrorCount"));
        status(category, entries,
                "Multipliers: formation " + decimal(values, "aiFormationMultiplier")
                        + "  separation " + decimal(values, "aiSeparationMultiplier")
                        + "  cohesion " + decimal(values, "aiCohesionMultiplier")
                        + "  search " + decimal(values, "aiSearchRadiusMultiplier"));

        String rationale = text(values, "aiRationale", "");
        if (!rationale.isBlank()) {
            status(category, entries, "Rationale: " + abbreviate(rationale, 180));
        }
        String error = text(values, "aiLastError", "");
        if (!error.isBlank()) {
            status(category, entries, "Last error: " + abbreviate(error, 180));
        }

    }

    private static void buildEngineering(
            ConfigCategory category,
            ConfigEntryBuilder entries,
            Map<String, String> values
    ) {
        toggle(category, entries, values,
                "Zombie engineering",
                "engineeringEnabled",
                "toggle_engineering",
                true,
                "Allows bounded breaking/bridging after navigation escalation.");

        doubleField(category, entries, values,
                "Max break hardness",
                "engineeringMaxHardness",
                "engineering_hardness_delta",
                2.0,
                "Server still enforces block-entity and mobGriefing safety checks.");

        intField(category, entries, values,
                "Max carried blocks",
                "engineeringMaxCarry",
                "engineering_carry_delta",
                4,
                "Maximum engineering material carried per Zombie.");

        doubleField(category, entries, values,
                "Task radius (blocks)",
                "engineeringTaskRadius",
                "engineering_radius_delta",
                8.0,
                "Local request/claim radius.");

        intField(category, entries, values,
                "Task advertisement TTL (ticks)",
                "engineeringTaskTtl",
                "engineering_ttl_delta",
                40,
                "Task advertisement lifetime; committed execution uses a separate bounded lease.");

        doubleField(category, entries, values,
                "Material handoff radius",
                "engineeringHandoffRadius",
                "engineering_handoff_delta",
                2.5,
                "Maximum range for one-block material handoff.");

        intField(category, entries, values,
                "Max bridge span",
                "engineeringMaxBridgeSpan",
                "engineering_bridge_delta",
                4,
                "Maximum bounded bridge gap span.");

        status(category, entries,
                "Live: engineers " + integer(values, "specEngineer")
                        + "  carriers " + integer(values, "specCarrier")
                        + "  | broken " + integer(values, "metricEngineeringBroken")
                        + "  placed " + integer(values, "metricEngineeringPlaced")
                        + "  carried " + integer(values, "metricEngineeringCarried"));

        status(category, entries,
                "Requests / claimed / completed: "
                        + integer(values, "metricEngineeringRequests") + " / "
                        + integer(values, "metricEngineeringClaimed") + " / "
                        + integer(values, "metricEngineeringCompleted"));

        action(category, entries,
                "Restore engineering baseline",
                "engineering_baseline",
                "Enable and Save to restore engineering defaults.");
    }

    private static void buildCoordination(
            ConfigCategory category,
            ConfigEntryBuilder entries,
            Map<String, String> values
    ) {
        toggle(category, entries, values,
                "Colony nest construction",
                "nestEnabled",
                "nest_toggle",
                false,
                "Optional colony construction (off by default).");

        toggle(category, entries, values,
                "Physical nest shell construction",
                "nestVisibleExpansionEnabled",
                "nest_visible_expansion_toggle",
                false,
                "OFF by default: spend actual soil/timber supplies on visible small shell modules; requires mobGriefing, loaded natural ground and an unoccupied site. Never replaces existing blocks.");

        toggle(category, entries, values,
                "Colony resource intake and growth",
                "nestLifecycleEnabled",
                "nest_lifecycle_toggle",
                false,
                "Consume actual dropped items; allow limited spawn cycles near players.");

        intField(category, entries, values,
                "Maximum local nest population",
                "nestMaxPopulation",
                "nest_max_population_delta",
                12,
                "Growth pauses when the local supported monster count reaches this cap.");

        toggle(category, entries, values,
                "Zombie worker item hauling",
                "nestHaulingEnabled", "nest_haul_toggle", false,
                "OFF by default: idle colony members move existing dirt, logs and food item entities to their own loaded nest; no auto-crafting.");

        toggle(category, entries, values,
                "Local ant-style pheromone signals",
                "nestPheromonesEnabled", "nest_pheromone_toggle", true,
                "Spatial FOOD / TIMBER / SOIL / STOP cues, deposited by actual scouts and physical returns, evaporate and influence worker decisions. Requires opt-in colony lifecycle and hauling.");

        toggle(category, entries, values,
                "Idle workers follow pheromone gradients",
                "nestPheromoneExplorationEnabled", "nest_pheromone_explore_toggle", true,
                "Lower-priority than hunting, hauling and work. Worker senses nearby gradients and takes short exploratory hops only in loaded chunks.");

        toggle(category, entries, values,
                "Zombie hunts farm animals for food",
                "nestAnimalHuntEnabled", "nest_animal_hunt_toggle", false,
                "Adult pigs and chickens are preferred, with cows/sheep/rabbits as alternatives. Uses real melee and vanilla meat drops; transport still requires a Zombie hauler. Can affect livestock farms.");

        toggle(category, entries, values,
                "Zombie harvests soil, raw logs and mature crops",
                "nestBlockGatherEnabled", "nest_block_gather_toggle", false,
                "Workers may break actual world dirt/log blocks including player-built structures, and pick fully grown wheat/carrots/potatoes/beetroots/nether wart/cocoa/melons/pumpkins/mushrooms/sugar cane. All items remain in the world until hauled.");

        toggle(category, entries, values,
                "Adaptive nest resource targets (capacity-aware)",
                "nestAdaptiveStockEnabled", "nest_stock_adapt_toggle", true,
                "When lifecycle is enabled, soil/log demand follows chamber construction and food demand follows remaining population slots. Physical item intake accepts only outstanding category demand; surplus dropped items remain in the world. When the population cap is full, unnecessary harvesting stops.");

        toggle(category, entries, values,
                "Renewable crop replanting (consumes ONE physical seed)",
                "nestCropReplantEnabled", "nest_crop_replant_toggle", false,
                "OFF by default: after harvesting a mature wheat/carrot/potato/beetroot/nether-wart crop, consume one newly dropped actual planting item and replant a juvenile plant on suitable farm soil. Requires enabled block harvesting; no synthetic seeds or loot.");

        intField(category, entries, values,
                "Animal and block survey interval (ticks)",
                "nestGatherInterval", "nest_gather_interval_delta", 160,
                "Per-worker local survey interval (60-800 ticks). Expensive scans are also limited per dimension.");

        toggle(category, entries, values,
                "Renewable sweet-berry foraging (TEST WORLDS ONLY)",
                "nestBerryForagingEnabled", "nest_berry_forage_toggle", false,
                "OFF by default. Idle Zombies may pick mature sweet berries near a loaded core, spawning physical drops for later hauling. Does not cut logs or dig dirt. Can affect player farms: only use in a designated test world. Requires lifecycle, hauling and mobGriefing.");

        intField(category, entries, values,
                "Berry-foraging survey interval (ticks)",
                "nestBerryForageInterval", "nest_berry_forage_interval_delta", 200,
                "Each worker checks a small nearby set of ripe berry bushes infrequently (120-800 ticks).");

        intField(category, entries, values,
                "Worker item search radius",
                "nestHaulSearchRadius", "nest_haul_radius_delta", 8,
                "Search only nearby real dropped items (4-16 blocks); budgeted per dimension.");

        intField(category, entries, values,
                "Maximum one-stack hauling size",
                "nestHaulMaxStack", "nest_haul_stack_delta", 16,
                "Only item stacks up to this size may be transported; larger stacks remain where they dropped.");

        intField(category, entries, values,
                "Worker item search interval (ticks)",
                "nestHaulAttemptInterval", "nest_haul_interval_delta", 100,
                "Each worker surveys for an unclaimed dropped item infrequently.");

        toggle(category, entries, values,
                "Adaptive insect-inspired recruiting",
                "nestAdaptiveRecruitment",
                "nest_adaptive_toggle",
                true,
                "Use local job deficits and response thresholds to choose next monster type.");

        doubleField(category, entries, values,
                "Colony target: Zombie workers (share 0-1)",
                "nestWorkerShare", "nest_worker_share_delta", 0.40,
                "Desired worker share; the remaining population includes guards, scouts and reserves.");

        doubleField(category, entries, values,
                "Colony target: Skeleton guards (share 0-1)",
                "nestGuardShare", "nest_guard_share_delta", 0.25,
                "Desired guard share; combined ratios are normalized by local pressure.");

        doubleField(category, entries, values,
                "Colony response threshold theta",
                "nestResponseThreshold", "nest_response_threshold_delta", 0.55,
                "Experimental response s^2/(s^2+theta^2), inspired by social-insect models.");

        status(category, entries,
                bool(values, "colonyScienceAvailable")
                        ? "Colony science: last sampled core @ "
                                + text(values, "colonyScienceLocation", "unknown")
                                + "  sample age " + integer(values, "colonyScienceAgeTicks") + " ticks"
                        : "Colony science: no active loaded core sampled yet");

        if (bool(values, "colonyScienceAvailable")) {
            status(category, entries,
                    "Population: " + integer(values, "colonySciencePopulation")
                            + " / " + integer(values, "colonyScienceCapacity")
                            + " | occupancy " + decimal(values, "colonyScienceOccupancy")
                            + " | peak " + integer(values, "colonySciencePeak"));

            status(category, entries,
                    "Built shell levels: " + integer(values, "colonyScienceVisibleShellLevel")
                            + " / " + integer(values, "colonyScienceChamberLevel")
                            + " virtual room levels");

            status(category, entries,
                    "Chamber level: " + integer(values, "colonyScienceChamberLevel")
                            + " | module cost: 8 soil + 6 timber points when near full.");

            status(category, entries,
                    "Population trend per sample: " + integer(values, "colonyScienceDelta")
                            + " | EMA " + decimal(values, "colonyScienceMean")
                            + " | samples " + integer(values, "colonyScienceSamples"));

            status(category, entries,
                    "Composition: zombie workers " + integer(values, "colonyScienceWorkers")
                            + ", skeleton guards " + integer(values, "colonyScienceGuards")
                            + ", spider scouts " + integer(values, "colonyScienceScouts")
                            + ", creeper reserves " + integer(values, "colonyScienceReserves"));

            status(category, entries,
                    "Worker logistics: delivered items " + integer(values, "colonyHaulItems")
                            + " | completed trips " + integer(values, "colonyHaulTrips")
                            + " (last sampled loaded core)");

            status(category, entries,
                    "Real berry items picked (not necessarily delivered): "
                            + integer(values, "colonyForagedBerries"));

            status(category, entries,
                    "Local work assignments " + integer(values, "colonyActiveWorkSites")
                            + " | Spider dropped-item leads "
                            + integer(values, "colonyScoutItemLeads"));
            status(category, entries,
                    "Spider resource/prey sightings: "
                            + integer(values, "colonyOpportunityLeads")
                            + " active leads | "
                            + integer(values, "colonyOpportunityWorkers")
                            + " worker reservations (max 6)");
            status(category, entries,
                    "Shared task intelligence: "
                            + integer(values, "colonyOpportunityReports")
                            + " recorded observations | "
                            + integer(values, "colonyOpportunityInvalidations")
                            + " stale/completed reports removed");
            status(category, entries,
                    "Returning-forager feedback: successful cargo trips "
                            + integer(values, "colonyReinforcedTrips")
                            + " | failed/unreachable work signals "
                            + integer(values, "colonyInhibitedJobs"));
            status(category, entries,
                    "Recruitment cues [food / timber / soil]: "
                            + decimal(values, "colonyFoodRecruitment") + " / "
                            + decimal(values, "colonyTimberRecruitment") + " / "
                            + decimal(values, "colonySoilRecruitment"));
            status(category, entries,
                    "Stop/inhibition cues [food / timber / soil]: "
                            + decimal(values, "colonyFoodInhibition") + " / "
                            + decimal(values, "colonyTimberInhibition") + " / "
                            + decimal(values, "colonySoilInhibition"));
            status(category, entries,
                    "Research-inspired feedback values are experimental GAME parameters, not measured ant or bee signal rates.");
            status(category, entries,
                    "Population-aware stock targets [soil / timber / food]: "
                            + integer(values, "colonyTargetSoil") + " / "
                            + integer(values, "colonyTargetTimber") + " / "
                            + integer(values, "colonyTargetFood"));
            status(category, entries,
                    "Registered visible nest pieces: "
                            + integer(values, "colonyOwnedShellPieces")
                            + " (workers cannot mine their own completed modules)");
            status(category, entries,
                    "Sparse pheromone field: " + integer(values, "colonyPheromoneCells")
                            + " / 128 occupied 4-block cells");
            status(category, entries,
                    "Real scout observations " + integer(values, "colonyPheromoneObserved")
                            + " | Successful route reinforcements "
                            + integer(values, "colonyPheromoneReinforced")
                            + " | Local stop traces " + integer(values, "colonyPheromoneStopped"));

            status(category, entries,
                    "Stored points: soil " + integer(values, "colonyScienceSoil")
                            + ", timber " + integer(values, "colonyScienceTimber")
                            + ", nutrients " + integer(values, "colonyScienceNutrient")
                            + ", legacy " + integer(values, "colonyScienceLegacy")
                            + " / total " + integer(values, "colonyScienceTotal"));

            status(category, entries,
                    "Nutrient readiness: " + decimal(values, "colonyScienceFoodReadiness")
                            + " | births " + integer(values, "colonyScienceBirths")
                            + " | suggested recruit " + text(values, "colonyScienceNextRecruit", "NONE"));

            status(category, entries,
                    "Response indices [0..1]: worker " + decimal(values, "colonyScienceWorkerResponse")
                            + " guard " + decimal(values, "colonyScienceGuardResponse")
                            + " scout " + decimal(values, "colonyScienceScoutResponse")
                            + " reserve " + decimal(values, "colonyScienceReserveResponse"));
        }

        intField(category, entries, values,
                "Nest survey interval (ticks)",
                "nestBuildInterval",
                "nest_interval_delta",
                200,
                "Controls idle site survey frequency.");

        intField(category, entries, values,
                "Minimum local colony size",
                "nestMinPopulation",
                "nest_population_delta",
                3,
                "Required local agent count.");

        status(category, entries,
                "Nest cores founded by currently loaded agents: "
                        + integer(values, "nestCoresFoundedByLoadedAgents"));

        action(category, entries,
                "Restore colony defaults",
                "nest_baseline",
                "Reset colony construction settings.");

        toggle(category, entries, values,
                "Dynamic division of labor",
                "divisionEnabled",
                "toggle_division",
                true,
                "Enable temporary task/specialization assignment.");

        toggle(category, entries, values,
                "Local support-position optimization",
                "supportPositionOptimizationEnabled",
                "support_position_toggle",
                true,
                "Geometry-only assist for real same-target ranged support: filter blocked/teammate-occupied options and compare travel distance plus separation deficit. The local planner and vanilla mob behavior remain in control. Turn OFF for original side selection.");

        intField(category, entries, values,
                "Formation lane hysteresis (ticks)",
                "formationHysteresis",
                "formation_hysteresis_delta",
                20,
                "Hold time before switching formation lanes.");

        intField(category, entries, values,
                "Role reassignment hysteresis (ticks)",
                "roleHysteresis",
                "role_hysteresis_delta",
                12,
                "Hold time before switching tactical responsibility.");

        intField(category, entries, values,
                "Minimum specialization hold (ticks)",
                "specializationHoldTicks",
                "specialization_hold_delta",
                30,
                "Minimum time a temporary specialization is retained.");

        doubleField(category, entries, values,
                "Specialization experience gain",
                "specializationExperienceGain",
                "specialization_gain_delta",
                0.025,
                "Experience gain per qualifying update.");

        doubleField(category, entries, values,
                "Specialization experience retention",
                "specializationExperienceDecay",
                "specialization_decay_delta",
                0.995,
                "Retention factor for specialization experience.");

        status(category, entries,
                "Specialists: engineer " + integer(values, "specEngineer")
                        + ", carrier " + integer(values, "specCarrier")
                        + ", scout " + integer(values, "specScout")
                        + ", interceptor " + integer(values, "specInterceptor")
                        + ", overwatch " + integer(values, "specOverwatch")
                        + ", lead breacher " + integer(values, "specLeadBreacher"));

        status(category, entries,
                "Support positions: active agents " + integer(values, "optimizedSupportAgents")
                        + "  | lane switches " + integer(values, "supportLaneSwitches"));
        status(category, entries,
                "Local feasibility samples: valid " + integer(values, "supportFeasibleSamples")
                        + "  | no feasible side " + integer(values, "supportUnavailableSamples"));
        status(category, entries,
                "Live activity modes [WORK / ALERT / COMBAT / RECOVERY]: "
                        + integer(values, "modeWorkAgents") + " / "
                        + integer(values, "modeAlertAgents") + " / "
                        + integer(values, "modeCombatAgents") + " / "
                        + integer(values, "modeRecoveryAgents"));
        status(category, entries,
                "Work uses resource supply, pheromones and task allocation; sensing/coordination runs in active encounters. There is no artificial combat turn system.");

        status(category, entries,
                "Target-scoped squads: agents with allies "
                        + integer(values, "liveTacticalAlliedAgents")
                        + "  directed peer links " + integer(values, "liveTacticalPeerLinks"));
        status(category, entries,
                "Agents supported by a same-target Creeper: "
                        + integer(values, "liveTacticalBreacherSupport")
                        + " (different-target Creepers do not count).");

        action(category, entries,
                "Restore coordination baseline",
                "coord_baseline",
                "Enable and Save to restore coordination defaults.");
        action(category, entries,
                "Restore labor baseline",
                "labor_baseline",
                "Enable and Save to restore division-of-labor defaults.");
    }

    private static void buildSensing(
            ConfigCategory category,
            ConfigEntryBuilder entries,
            Map<String, String> values
    ) {
        toggle(category, entries, values,
                "Sensing imperfection",
                "sensingEnabled",
                "toggle_sensing",
                false,
                "Enable deterministic sensing noise/dropout experiments.");

        doubleField(category, entries, values,
                "Sensing dropout rate",
                "sensingDrop",
                "sensing_drop_delta",
                0.0,
                "0.0 = no forced dropouts; server clamps to the valid range.");

        doubleField(category, entries, values,
                "Horizontal sensing noise (blocks)",
                "sensingNoise",
                "sensing_noise_delta",
                0.0,
                "Maximum deterministic horizontal measurement error.");

        toggle(category, entries, values,
                "Communication",
                "commEnabled",
                "toggle_comm",
                true,
                "Enable local target-message exchange.");

        doubleField(category, entries, values,
                "Communication packet drop rate",
                "commDrop",
                "comm_drop_delta",
                0.0,
                "Experimental packet loss rate.");

        intField(category, entries, values,
                "Communication latency (ticks)",
                "commLatency",
                "comm_latency_delta",
                0,
                "Artificial delivery latency.");

        doubleField(category, entries, values,
                "Communication radius (blocks)",
                "commRadius",
                "comm_radius_delta",
                16.0,
                "Local peer communication radius.");

        status(category, entries,
                "Observed comms: accepted " + integer(values, "metricCommAccepted")
                        + "  delivered " + integer(values, "metricCommDelivered")
                        + "  dropped " + integer(values, "metricCommDropped"));

        action(category, entries, "Restore sensing baseline", "sensing_baseline",
                "Enable and Save to disable sensing faults.");
        action(category, entries, "Restore communication baseline", "comm_baseline",
                "Enable and Save to restore communication defaults.");
    }

    private static void buildSearch(
            ConfigCategory category,
            ConfigEntryBuilder entries,
            Map<String, String> values
    ) {
        doubleField(category, entries, values,
                "SEARCH confidence threshold",
                "searchThreshold",
                "search_threshold_delta",
                0.45,
                "Indirect target confidence below this enters SEARCH.");

        doubleField(category, entries, values,
                "SEARCH speed factor",
                "searchSpeed",
                "search_speed_delta",
                1.0,
                "Movement factor used during decentralized search.");

        doubleField(category, entries, values,
                "SEARCH max radius",
                "searchMaxRadius",
                "search_max_radius_delta",
                10.0,
                "Maximum expanding search radius.");

        toggle(category, entries, values,
                "Target prediction",
                "predictionEnabled",
                "toggle_prediction",
                true,
                "Enable conservative short-horizon prediction.");

        doubleField(category, entries, values,
                "Prediction max distance",
                "predictionDistance",
                "prediction_distance_delta",
                3.5,
                "Hard bound on prediction offset.");

        status(category, entries,
                "Search episodes: started " + integer(values, "metricSearchStarted")
                        + "  succeeded " + integer(values, "metricSearchSucceeded")
                        + "  failed " + integer(values, "metricSearchFailed")
                        + "  active " + integer(values, "metricActiveSearch"));

        action(category, entries,
                "Restore search/prediction baseline",
                "search_baseline",
                "Enable and Save to restore search defaults.");
    }

    private static void buildNavigation(
            ConfigCategory category,
            ConfigEntryBuilder entries,
            Map<String, String> values
    ) {
        toggle(category, entries, values,
                "Obstacle avoidance",
                "obstacleEnabled",
                "toggle_obstacle",
                true,
                "Enable short-range obstacle probing.");

        doubleField(category, entries, values,
                "Obstacle lookahead (blocks)",
                "navLookahead",
                "nav_lookahead_delta",
                1.5,
                "Forward obstacle probe distance.");

        doubleField(category, entries, values,
                "Lateral detour (blocks)",
                "navLateral",
                "nav_lateral_delta",
                1.5,
                "Local lateral candidate spacing.");

        toggle(category, entries, values,
                "Walkability checks",
                "walkabilityEnabled",
                "toggle_walkability",
                true,
                "Reject unsupported local probe points.");

        intField(category, entries, values,
                "Accepted drop (blocks)",
                "navMaxDrop",
                "nav_max_drop_delta",
                1,
                "Maximum local probe drop.");

        intField(category, entries, values,
                "Stuck window (ticks)",
                "stuckWindow",
                "nav_stuck_window_delta",
                24,
                "Observation window before recovery planning.");

        doubleField(category, entries, values,
                "Minimum progress (blocks)",
                "stuckMinProgress",
                "nav_stuck_progress_delta",
                0.75,
                "Required movement during the stuck window.");

        doubleField(category, entries, values,
                "Recovery distance (blocks)",
                "recoveryDistance",
                "nav_recovery_distance_delta",
                2.0,
                "Lateral distance for deterministic recovery.");

        intField(category, entries, values,
                "Recovery duration (ticks)",
                "recoveryDuration",
                "nav_recovery_duration_delta",
                18,
                "Bounded recovery waypoint lease.");

        doubleField(category, entries, values,
                "Progress weight",
                "navProgressWeight",
                "nav_progress_weight_delta",
                1.0,
                "Planner reward for target progress.");

        doubleField(category, entries, values,
                "Lateral penalty",
                "navLateralPenalty",
                "nav_lateral_penalty_delta",
                0.20,
                "Planner penalty for excessive lateral movement.");

        doubleField(category, entries, values,
                "Congestion penalty",
                "navCongestionPenalty",
                "nav_congestion_penalty_delta",
                0.75,
                "Planner penalty for crowded candidates.");

        doubleField(category, entries, values,
                "Congestion radius",
                "navCongestionRadius",
                "nav_congestion_radius_delta",
                2.5,
                "Radius used to estimate local crowding.");

        toggle(category, entries, values,
                "Path evidence",
                "navPathEvidenceEnabled",
                "toggle_path_evidence",
                true,
                "Use PathNavigation evidence to validate local candidates.");

        intField(category, entries, values,
                "Path-evidence budget per server tick",
                "navPathBudgetPerTick",
                "nav_path_budget_delta",
                96,
                "Shared across this dimension. When exhausted, complete planner episodes defer instead of assuming untested paths work.");


        doubleField(category, entries, values,
                "Path node penalty",
                "navPathNodePenalty",
                "nav_path_node_penalty_delta",
                0.05,
                "Penalty per path node.");

        doubleField(category, entries, values,
                "Path residual penalty",
                "navPathResidualPenalty",
                "nav_path_residual_penalty_delta",
                0.25,
                "Penalty for residual distance to path target.");

        doubleField(category, entries, values,
                "Max path residual",
                "navPathMaxResidual",
                "nav_path_max_residual_delta",
                1.5,
                "Maximum accepted path residual distance.");

        status(category, entries,
                "Runtime: detours " + integer(values, "metricDetours")
                        + "  recoveries " + integer(values, "metricRecoveries")
                        + "  recovery failures " + integer(values, "metricRecoveryFailures")
                        + "  path queries " + integer(values, "metricPathQueries"));

        status(category, entries,
                "Path budget this tick: " + integer(values, "pathBudgetUsed")
                        + " / " + integer(values, "navPathBudgetPerTick")
                        + "  | waiting agents " + integer(values, "pathBudgetWaiters"));
        status(category, entries,
                "Budget lifetime reservations: granted " + integer(values, "pathBudgetGranted")
                        + "  deferred " + integer(values, "pathBudgetDeferred")
                        + " (a deferred episode is not a failed path).");

        status(category, entries,
                "Navigation commands: issued " + integer(values, "metricNavCommandsIssued")
                        + "  skipped " + integer(values, "metricNavCommandsSkipped"));
        status(category, entries,
                "Navigation retries after completion: " + integer(values, "metricNavRetries")
                        + "  periodic active refreshes: " + integer(values, "metricNavRefreshes"));

        action(category, entries,
                "Restore navigation baseline",
                "nav_baseline",
                "Enable and Save to restore navigation defaults.");
    }

    private static void buildExperiment(
            ConfigCategory category,
            ConfigEntryBuilder entries,
            Map<String, String> values
    ) {
        status(category, entries,
                "Preset: " + text(values, "activePreset", "CUSTOM")
                        + "  |  active=" + bool(values, "experimentActive")
                        + "  |  elapsed=" + integer(values, "experimentElapsedTicks") + " ticks"
                        + "  |  agents=" + integer(values, "experimentAgents"));

        intField(category, entries, values,
                "Experiment seed",
                "experimentSeed",
                "experiment_seed_delta",
                integer(values, "experimentSeed"),
                "Deterministic experiment seed.");

        action(category, entries, "Start experiment", "experiment_start",
                "Enable and Save to start/reset the current experiment timing window.");
        action(category, entries, "Reset experiment metrics", "experiment_reset",
                "Enable and Save to reset experiment metrics.");

        status(category, entries,
                "Recovery failure rate: " + decimal(values, "metricRecoveryFailureRate")
                        + "  | observed comm drop: " + decimal(values, "metricObservedCommDropRate")
                        + "  | search success: " + decimal(values, "metricSearchSuccessRate")
                        + "  | avg reacquisition: " + decimal(values, "metricAvgReacquisitionTicks") + " ticks");
    }

    private static void toggle(
            ConfigCategory category,
            ConfigEntryBuilder entries,
            Map<String, String> values,
            String label,
            String key,
            String action,
            boolean defaultValue,
            String tooltip
    ) {
        boolean initial = bool(values, key);
        category.addEntry(entries.startBooleanToggle(Component.literal(label), initial)
                .setDefaultValue(defaultValue)
                .setTooltip(Component.literal(tooltip))
                .setSaveConsumer(next -> {
                    if (next != initial) {
                        SwarmControlClient.sendAction("set:" + action, next ? 1.0 : 0.0);
                    }
                })
                .build());
    }

    private static void intField(
            ConfigCategory category,
            ConfigEntryBuilder entries,
            Map<String, String> values,
            String label,
            String key,
            String action,
            int defaultValue,
            String tooltip
    ) {
        int initial = integer(values, key);
        category.addEntry(entries.startIntField(Component.literal(label), initial)
                .setDefaultValue(defaultValue)
                .setTooltip(Component.literal(tooltip))
                .setSaveConsumer(next -> {
                    if (next != initial) {
                        SwarmControlClient.sendAction("set:" + action, next);
                    }
                })
                .build());
    }

    private static void doubleField(
            ConfigCategory category,
            ConfigEntryBuilder entries,
            Map<String, String> values,
            String label,
            String key,
            String action,
            double defaultValue,
            String tooltip
    ) {
        double initial = number(values, key);
        category.addEntry(entries.startDoubleField(Component.literal(label), initial)
                .setDefaultValue(defaultValue)
                .setTooltip(Component.literal(tooltip))
                .setSaveConsumer(next -> {
                    if (Math.abs(next - initial) > 1.0e-9) {
                        SwarmControlClient.sendAction("set:" + action, next);
                    }
                })
                .build());
    }

    private static void action(
            ConfigCategory category,
            ConfigEntryBuilder entries,
            String label,
            String action,
            String tooltip
    ) {
        category.addEntry(entries.startBooleanToggle(Component.literal(label), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal(tooltip))
                .setSaveConsumer(run -> {
                    if (run) {
                        SwarmControlClient.sendAction(action, 0.0);
                    }
                })
                .build());
    }

    private static void status(
            ConfigCategory category,
            ConfigEntryBuilder entries,
            String value
    ) {
        category.addEntry(entries.startTextDescription(Component.literal(value)).build());
    }

    private static Map<String, String> parseSnapshot(String snapshot) {
        Map<String, String> values = new HashMap<>();
        if (snapshot == null || snapshot.isBlank()) {
            return values;
        }

        for (String part : snapshot.split(";")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || separator >= part.length() - 1) {
                continue;
            }
            values.put(part.substring(0, separator), part.substring(separator + 1));
        }
        return values;
    }

    private static boolean bool(Map<String, String> values, String key) {
        return Boolean.parseBoolean(values.getOrDefault(key, "false"));
    }

    private static int integer(Map<String, String> values, String key) {
        return (int) Math.round(number(values, key));
    }

    private static double number(Map<String, String> values, String key) {
        try {
            return Double.parseDouble(values.getOrDefault(key, "0"));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static String decimal(Map<String, String> values, String key) {
        return String.format(java.util.Locale.ROOT, "%.2f", number(values, key));
    }

    private static String text(Map<String, String> values, String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
