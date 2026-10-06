package dev.swarmmobs.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

public final class SwarmControlScreen extends Screen {
    private enum Page {
        OVERVIEW("Overview"),
        AI("AI"),
        EXPERIMENT("Experiment"),
        LABOR("Labor"),
        ENGINEERING("Engineering"),
        COORDINATION("Coordination"),
        SENSING("Sensing"),
        COMMUNICATION("Communication"),
        SEARCH("Search & Prediction"),
        NAVIGATION("Navigation");

        private final String label;

        Page(String label) {
            this.label = label;
        }
    }

    private final Map<String, String> values = new HashMap<>();
    private Page page = Page.OVERVIEW;

    public SwarmControlScreen(String snapshot) {
        super(Component.literal("Swarm Mobs Control Panel"));
        parseSnapshot(snapshot);
    }

    public void updateSnapshot(String snapshot) {
        parseSnapshot(snapshot);
        if (minecraft != null) {
            clearWidgets();
            buildWidgets();
        }
    }

    @Override
    protected void init() {
        super.init();
        buildWidgets();
    }

    private void buildWidgets() {
        int center = width / 2;
        int top = 40;
        int tabWidth = 112;
        int tabsPerRow = 5;
        int rowWidth = tabWidth * tabsPerRow;
        int startX = center - rowWidth / 2;

        Page[] pages = Page.values();
        for (int i = 0; i < pages.length; i++) {
            Page target = pages[i];
            int row = i / tabsPerRow;
            int column = i % tabsPerRow;
            addRenderableWidget(
                    Button.builder(
                            Component.literal((page == target ? "§a" : "") + target.label),
                            button -> {
                                page = target;
                                clearWidgets();
                                buildWidgets();
                            }
                    ).bounds(
                            startX + column * tabWidth,
                            top + row * 24,
                            tabWidth - 4,
                            20
                    ).build()
            );
        }

        addRenderableWidget(
                Button.builder(
                        Component.literal("§cRestore ALL Baselines"),
                        button -> SwarmControlClient.sendAction("baseline_all", 0.0)
                ).bounds(center - 100, height - 46, 200, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal("Done"),
                        button -> onClose()
                ).bounds(center - 50, height - 24, 100, 20).build()
        );

        switch (page) {
            case OVERVIEW -> buildOverview();
            case AI -> buildAi();
            case EXPERIMENT -> buildExperiment();
            case LABOR -> buildLabor();
            case ENGINEERING -> buildEngineering();
            case COORDINATION -> buildCoordination();
            case SENSING -> buildSensing();
            case COMMUNICATION -> buildCommunication();
            case SEARCH -> buildSearch();
            case NAVIGATION -> buildNavigation();
        }
    }

    private void buildOverview() {
        int leftX = width / 2 - 310;
        int rightX = width / 2 + 10;
        int y = 108;

        addStatusRow(leftX, y, "Swarm agents",
                Integer.toString((int) number("liveAgents")));
        addStatusRow(rightX, y, "Master",
                bool("master") ? "§aENABLED" : "§cDISABLED");
        y += 28;

        addStatusRow(leftX, y, "Species",
                "Z " + (int) number("liveZombies")
                        + "  S " + (int) number("liveSkeletons")
                        + "  Sp " + (int) number("liveSpiders")
                        + "  C " + (int) number("liveCreepers"));
        addStatusRow(rightX, y, "AI",
                bool("aiActive")
                        ? "§aACTIVE " + values.getOrDefault("aiActiveMode", "BASELINE")
                        : (bool("aiEnabled") ? "§eSHADOW READY" : "§7OFF"));
        y += 28;

        addStatusRow(leftX, y, "Tasks",
                "ENG " + (int) number("taskEngineering")
                        + "  MAT " + (int) number("taskMaterial")
                        + "  FLK " + (int) number("taskFlank")
                        + "  RNG " + (int) number("taskRanged"));
        addStatusRow(rightX, y, "Frontline",
                "BR " + (int) number("taskBreach")
                        + "  SRCH " + (int) number("taskSearch")
                        + "  RSV " + (int) number("taskReserve"));
        y += 28;

        addStatusRow(leftX, y, "Specialists",
                "Engineer " + (int) number("specEngineer")
                        + "  Carrier " + (int) number("specCarrier")
                        + "  Scout " + (int) number("specScout"));
        addStatusRow(rightX, y, "Combat specialists",
                "Interceptor " + (int) number("specInterceptor")
                        + "  Overwatch " + (int) number("specOverwatch")
                        + "  Lead Breacher " + (int) number("specLeadBreacher"));
        y += 34;

        addRenderableWidget(
                Button.builder(
                        Component.literal("Refresh live swarm snapshot"),
                        button -> SwarmControlClient.requestPanel()
                ).bounds(leftX, y, 300, 20).build()
        );
        addRenderableWidget(
                Button.builder(
                        Component.literal("Experiment snapshot"),
                        button -> SwarmControlClient.sendAction("experiment_snapshot", 0.0)
                ).bounds(rightX, y, 300, 20).build()
        );
    }

    private void buildLabor() {
        int leftX = width / 2 - 310;
        int rightX = width / 2 + 10;
        int y = 108;

        addToggleRow(leftX, y, "Dynamic division of labor", bool("divisionEnabled"), "toggle_division");
        addStatusRow(rightX, y, "Live task allocation",
                "ENG " + (int) number("taskEngineering")
                        + " / MAT " + (int) number("taskMaterial")
                        + " / FLANK " + (int) number("taskFlank"));
        y += 28;

        addNumericRow(leftX, y, "Minimum specialization hold",
                (int) number("specializationHoldTicks") + " ticks",
                "specialization_hold_delta", 5.0);
        addStatusRow(rightX, y, "Ranged / breach / reserve",
                (int) number("taskRanged")
                        + " / " + (int) number("taskBreach")
                        + " / " + (int) number("taskReserve"));
        y += 28;

        addNumericRow(leftX, y, "Experience gain",
                format(number("specializationExperienceGain")),
                "specialization_gain_delta", 0.005);
        addStatusRow(rightX, y, "Engineer / carrier / scout",
                (int) number("specEngineer")
                        + " / " + (int) number("specCarrier")
                        + " / " + (int) number("specScout"));
        y += 28;

        addNumericRow(leftX, y, "Experience retention",
                format(number("specializationExperienceDecay")),
                "specialization_decay_delta", 0.001);
        addStatusRow(rightX, y, "Interceptor / overwatch",
                (int) number("specInterceptor")
                        + " / " + (int) number("specOverwatch"));
        y += 34;

        addRenderableWidget(
                Button.builder(
                        Component.literal("Labor baseline"),
                        button -> SwarmControlClient.sendAction("labor_baseline", 0.0)
                ).bounds(leftX, y, 300, 20).build()
        );
        addStatusRow(rightX, y, "Design",
                "Species capability -> demand -> bid -> temporary specialization");
    }

    private void buildEngineering() {
        int leftX = width / 2 - 310;
        int rightX = width / 2 + 10;
        int y = 108;

        addToggleRow(leftX, y, "Zombie engineering", bool("engineeringEnabled"), "toggle_engineering");
        addStatusRow(rightX, y, "Escalation",
                "PLAN -> DETOUR -> RECOVERY -> ENGINEERING");
        y += 28;

        addNumericRow(leftX, y, "Max break hardness",
                format(number("engineeringMaxHardness")),
                "engineering_hardness_delta", 0.25);
        addStatusRow(rightX, y, "Live engineers / carriers",
                (int) number("specEngineer") + " / " + (int) number("specCarrier"));
        y += 28;

        addNumericRow(leftX, y, "Max carried blocks",
                Integer.toString((int) number("engineeringMaxCarry")),
                "engineering_carry_delta", 1.0);
        addStatusRow(rightX, y, "Blocks broken / placed",
                (int) number("metricEngineeringBroken")
                        + " / " + (int) number("metricEngineeringPlaced"));
        y += 28;

        addNumericRow(leftX, y, "Task radius",
                format(number("engineeringTaskRadius")) + " blocks",
                "engineering_radius_delta", 1.0);
        addStatusRow(rightX, y, "Carried materials",
                Integer.toString((int) number("metricEngineeringCarried")));
        y += 28;

        addNumericRow(leftX, y, "Task TTL",
                (int) number("engineeringTaskTtl") + " ticks",
                "engineering_ttl_delta", 10.0);
        addStatusRow(rightX, y, "Requests / claimed / completed",
                (int) number("metricEngineeringRequests")
                        + " / " + (int) number("metricEngineeringClaimed")
                        + " / " + (int) number("metricEngineeringCompleted"));
        y += 28;

        addNumericRow(leftX, y, "Material handoff radius",
                format(number("engineeringHandoffRadius")) + " blocks",
                "engineering_handoff_delta", 0.25);
        addStatusRow(rightX, y, "Safety",
                "HARD + mobGriefing + bounded hardness + no block entities");
        y += 28;

        addNumericRow(leftX, y, "Max bridge span",
                Integer.toString((int) number("engineeringMaxBridgeSpan")),
                "engineering_bridge_delta", 1.0);
        y += 34;

        addRenderableWidget(
                Button.builder(
                        Component.literal("Engineering baseline"),
                        button -> SwarmControlClient.sendAction("engineering_baseline", 0.0)
                ).bounds(leftX, y, 300, 20).build()
        );
        addStatusRow(rightX, y, "Recovery-aware",
                "Engineering can escalate after failed navigation recovery");
    }

    private void buildAi() {
        int leftX = width / 2 - 310;
        int rightX = width / 2 + 10;
        int y = 108;

        addToggleRow(
                leftX, y,
                "Local Ollama strategy service",
                bool("aiEnabled"),
                "ai_toggle"
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Model: " + values.getOrDefault("aiModel", "(select with /swarmmobs ai model <name>)")
                        ),
                        button -> {
                        }
                ).bounds(rightX, y, 300, 20).build()
        );
        y += 30;

        addToggleRow(
                leftX, y,
                "Apply bounded AI strategy",
                bool("aiActiveEnabled"),
                "ai_active_toggle"
        );
        addStatusRow(
                rightX, y,
                "Active overlay",
                bool("aiActive")
                        ? "§a" + values.getOrDefault("aiActiveMode", "BASELINE")
                                + " (" + (int) number("aiActiveExpiresIn") + " ticks)"
                        : "§7DETERMINISTIC BASELINE"
        );
        y += 30;

        addRenderableWidget(
                Button.builder(
                        Component.literal("Run Strategy Recommendation"),
                        button -> SwarmControlClient.sendAction("ai_shadow", 0.0)
                ).bounds(leftX, y, 300, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal("Refresh AI State"),
                        button -> SwarmControlClient.sendAction("ai_refresh", 0.0)
                ).bounds(rightX, y, 300, 20).build()
        );
        y += 30;

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "State=" + values.getOrDefault("aiStatus", "IDLE")
                                        + " | inFlight=" + bool("aiInFlight")
                                        + " | latency=" + (int) number("aiLatencyMs") + " ms"
                        ),
                        button -> {
                        }
                ).bounds(leftX, y, 300, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Provider=" + values.getOrDefault("aiProvider", "none")
                                        + " | mode=" + values.getOrDefault("aiMode", "BASELINE")
                        ),
                        button -> {
                        }
                ).bounds(rightX, y, 300, 20).build()
        );
        y += 30;

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "formation=" + format(number("aiFormationMultiplier"))
                                        + " separation=" + format(number("aiSeparationMultiplier"))
                        ),
                        button -> {
                        }
                ).bounds(leftX, y, 300, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "cohesion=" + format(number("aiCohesionMultiplier"))
                                        + " searchRadius=" + format(number("aiSearchRadiusMultiplier"))
                        ),
                        button -> {
                        }
                ).bounds(rightX, y, 300, 20).build()
        );
        y += 30;

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "success=" + (int) number("aiSuccessCount")
                                        + " fallback=" + (int) number("aiFallbackCount")
                                        + " errors=" + (int) number("aiErrorCount")
                        ),
                        button -> {
                        }
                ).bounds(leftX, y, 300, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                bool("aiActiveEnabled")
                                        ? "Active AI biases bounded high-level demand only"
                                        : "Shadow mode: recommendations do not affect gameplay"
                        ),
                        button -> {
                        }
                ).bounds(rightX, y, 300, 20).build()
        );
        y += 30;

        String rationale = values.getOrDefault("aiRationale", "");
        addRenderableWidget(
                Button.builder(
                        Component.literal("Rationale: " + abbreviate(rationale, 90)),
                        button -> {
                        }
                ).bounds(leftX, y, 620, 20).build()
        );
        y += 26;

        String error = values.getOrDefault("aiLastError", "");
        if (!error.isBlank()) {
            addRenderableWidget(
                    Button.builder(
                            Component.literal("Last error: " + abbreviate(error, 90)),
                            button -> {
                            }
                    ).bounds(leftX, y, 620, 20).build()
            );
        }
    }

    private void buildExperiment() {
        int leftX = width / 2 - 310;
        int rightX = width / 2 + 10;
        int y = 108;

        addRenderableWidget(
                Button.builder(
                        Component.literal("Active preset: " + values.getOrDefault("activePreset", "BASELINE")),
                        button -> {
                        }
                ).bounds(leftX, y, 300, 20).build()
        );
        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Run: " + (bool("experimentActive") ? "§aACTIVE" : "§7IDLE")
                                        + "  |  elapsed=" + (int) number("experimentElapsedTicks")
                                        + " ticks  |  agents=" + (int) number("experimentAgents")
                        ),
                        button -> SwarmControlClient.sendAction("experiment_snapshot", 0.0)
                ).bounds(rightX, y, 300, 20).build()
        );
        y += 28;

        addNumericRow(
                leftX, y,
                "Experiment seed",
                Integer.toString((int) number("experimentSeed")),
                "experiment_seed_delta",
                1.0
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal("Start"),
                        button -> SwarmControlClient.sendAction("experiment_start", 0.0)
                ).bounds(rightX, y, 94, 20).build()
        );
        addRenderableWidget(
                Button.builder(
                        Component.literal("Reset"),
                        button -> SwarmControlClient.sendAction("experiment_reset", 0.0)
                ).bounds(rightX + 103, y, 94, 20).build()
        );
        addRenderableWidget(
                Button.builder(
                        Component.literal("Refresh"),
                        button -> SwarmControlClient.sendAction("experiment_snapshot", 0.0)
                ).bounds(rightX + 206, y, 94, 20).build()
        );
        y += 34;

        addRenderableWidget(
                Button.builder(
                        Component.literal("Baseline"),
                        button -> SwarmControlClient.sendAction("preset_baseline", 0.0)
                ).bounds(leftX, y, 145, 20).build()
        );
        addRenderableWidget(
                Button.builder(
                        Component.literal("Noisy Sensing"),
                        button -> SwarmControlClient.sendAction("preset_noisy_sensing", 0.0)
                ).bounds(leftX + 155, y, 145, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Comm: accepted=" + (int) number("metricCommAccepted")
                                        + " delivered=" + (int) number("metricCommDelivered")
                                        + " dropped=" + (int) number("metricCommDropped")
                        ),
                        button -> {
                        }
                ).bounds(rightX, y, 300, 20).build()
        );
        y += 26;

        addRenderableWidget(
                Button.builder(
                        Component.literal("Lossy Comms"),
                        button -> SwarmControlClient.sendAction("preset_lossy_comms", 0.0)
                ).bounds(leftX, y, 145, 20).build()
        );
        addRenderableWidget(
                Button.builder(
                        Component.literal("Combined Faults"),
                        button -> SwarmControlClient.sendAction("preset_combined_faults", 0.0)
                ).bounds(leftX + 155, y, 145, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Observed comm drop=" + formatPercent(number("metricObservedCommDropRate"))
                                        + "  recover fail=" + formatPercent(number("metricRecoveryFailureRate"))
                        ),
                        button -> {
                        }
                ).bounds(rightX, y, 300, 20).build()
        );
        y += 26;

        addRenderableWidget(
                Button.builder(
                        Component.literal("Navigation Stress"),
                        button -> SwarmControlClient.sendAction("preset_navigation_stress", 0.0)
                ).bounds(leftX, y, 300, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Nav: detours=" + (int) number("metricDetours")
                                        + " recoveries=" + (int) number("metricRecoveries")
                                        + " pathQueries=" + (int) number("metricPathQueries")
                        ),
                        button -> {
                        }
                ).bounds(rightX, y, 300, 20).build()
        );
        y += 26;

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Recovery attempts=" + (int) number("metricRecoveryAttempts")
                                        + " failures=" + (int) number("metricRecoveryFailures")
                        ),
                        button -> {
                        }
                ).bounds(rightX, y, 300, 20).build()
        );
        y += 26;

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Role reassignments=" + (int) number("metricRoleReassignments")
                        ),
                        button -> {
                        }
                ).bounds(rightX, y, 300, 20).build()
        );
        y += 26;

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Search: started=" + (int) number("metricSearchStarted")
                                        + " success=" + (int) number("metricSearchSucceeded")
                                        + " failed=" + (int) number("metricSearchFailed")
                        ),
                        button -> {
                        }
                ).bounds(rightX, y, 300, 20).build()
        );
        y += 26;

        addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Reacquire: success=" + formatPercent(number("metricSearchSuccessRate"))
                                        + " avg=" + format(number("metricAvgReacquisitionTicks")) + " ticks"
                                        + " active=" + (int) number("metricActiveSearch")
                        ),
                        button -> {
                        }
                ).bounds(rightX, y, 300, 20).build()
        );
    }

    private void buildCoordination() {
        int x = width / 2 - 150;
        int y = 108;

        addNumericRow(
                x, y,
                "Formation lane hysteresis",
                Integer.toString((int) number("formationHysteresis")) + " ticks",
                "formation_hysteresis_delta",
                2.0
        );
        y += 28;

        addNumericRow(
                x, y,
                "Role reassignment hysteresis",
                Integer.toString((int) number("roleHysteresis")) + " ticks",
                "role_hysteresis_delta",
                2.0
        );
        y += 34;

        addRenderableWidget(
                Button.builder(
                        Component.literal("Coordination baseline"),
                        button -> SwarmControlClient.sendAction("coord_baseline", 0.0)
                ).bounds(x, y, 300, 20).build()
        );
    }

    private void buildSensing() {
        int x = width / 2 - 150;
        int y = 108;

        addToggleRow(
                x, y,
                "Imperfection",
                bool("sensingEnabled"),
                "toggle_sensing"
        );
        y += 28;

        addNumericRow(
                x, y,
                "Dropout rate",
                formatPercent(number("sensingDrop")),
                "sensing_drop_delta",
                0.05
        );
        y += 28;

        addNumericRow(
                x, y,
                "Horizontal noise",
                format(number("sensingNoise")) + " blocks",
                "sensing_noise_delta",
                0.25
        );
        y += 34;

        addRenderableWidget(
                Button.builder(
                        Component.literal("Sensing baseline"),
                        button -> SwarmControlClient.sendAction("sensing_baseline", 0.0)
                ).bounds(x, y, 300, 20).build()
        );
    }

    private void buildCommunication() {
        int x = width / 2 - 150;
        int y = 108;

        addToggleRow(
                x, y,
                "Communication",
                bool("commEnabled"),
                "toggle_comm"
        );
        y += 28;

        addNumericRow(
                x, y,
                "Packet drop",
                formatPercent(number("commDrop")),
                "comm_drop_delta",
                0.05
        );
        y += 28;

        addNumericRow(
                x, y,
                "Latency",
                Integer.toString((int) number("commLatency")) + " ticks",
                "comm_latency_delta",
                5.0
        );
        y += 28;

        addNumericRow(
                x, y,
                "Radius",
                format(number("commRadius")) + " blocks",
                "comm_radius_delta",
                2.0
        );
        y += 34;

        addRenderableWidget(
                Button.builder(
                        Component.literal("Communication baseline"),
                        button -> SwarmControlClient.sendAction("comm_baseline", 0.0)
                ).bounds(x, y, 300, 20).build()
        );
    }

    private void buildSearch() {
        int x = width / 2 - 150;
        int y = 108;

        addNumericRow(
                x, y,
                "SEARCH confidence",
                format(number("searchThreshold")),
                "search_threshold_delta",
                0.05
        );
        y += 28;

        addNumericRow(
                x, y,
                "SEARCH speed",
                format(number("searchSpeed")),
                "search_speed_delta",
                0.05
        );
        y += 28;

        addNumericRow(
                x, y,
                "SEARCH max radius",
                format(number("searchMaxRadius")) + " blocks",
                "search_max_radius_delta",
                1.0
        );
        y += 28;

        addToggleRow(
                x, y,
                "Prediction",
                bool("predictionEnabled"),
                "toggle_prediction"
        );
        y += 28;

        addNumericRow(
                x, y,
                "Prediction max distance",
                format(number("predictionDistance")) + " blocks",
                "prediction_distance_delta",
                0.5
        );
        y += 34;

        addRenderableWidget(
                Button.builder(
                        Component.literal("Search / prediction baseline"),
                        button -> SwarmControlClient.sendAction("search_baseline", 0.0)
                ).bounds(x, y, 300, 20).build()
        );
    }

    private void buildNavigation() {
        int leftX = width / 2 - 310;
        int rightX = width / 2 + 10;
        int leftY = 108;
        int rightY = 108;

        addToggleRow(
                leftX, leftY,
                "Obstacle avoidance",
                bool("obstacleEnabled"),
                "toggle_obstacle"
        );
        leftY += 28;

        addNumericRow(
                leftX, leftY,
                "Lookahead",
                format(number("navLookahead")) + " blocks",
                "nav_lookahead_delta",
                0.25
        );
        leftY += 28;

        addNumericRow(
                leftX, leftY,
                "Lateral detour",
                format(number("navLateral")) + " blocks",
                "nav_lateral_delta",
                0.25
        );
        leftY += 28;

        addToggleRow(
                leftX, leftY,
                "Walkability",
                bool("walkabilityEnabled"),
                "toggle_walkability"
        );
        leftY += 28;

        addNumericRow(
                leftX, leftY,
                "Accepted drop",
                Integer.toString((int) number("navMaxDrop")) + " blocks",
                "nav_max_drop_delta",
                1.0
        );
        leftY += 28;

        addNumericRow(
                leftX, leftY,
                "Stuck window",
                Integer.toString((int) number("stuckWindow")) + " ticks",
                "nav_stuck_window_delta",
                2.0
        );
        leftY += 28;

        addNumericRow(
                leftX, leftY,
                "Min progress",
                format(number("stuckMinProgress")) + " blocks",
                "nav_stuck_progress_delta",
                0.10
        );
        leftY += 28;

        addNumericRow(
                leftX, leftY,
                "Recovery distance",
                format(number("recoveryDistance")) + " blocks",
                "nav_recovery_distance_delta",
                0.25
        );
        leftY += 28;

        addNumericRow(
                leftX, leftY,
                "Recovery duration",
                Integer.toString((int) number("recoveryDuration")) + " ticks",
                "nav_recovery_duration_delta",
                3.0
        );

        addNumericRow(
                rightX, rightY,
                "Progress weight",
                format(number("navProgressWeight")),
                "nav_progress_weight_delta",
                0.10
        );
        rightY += 28;

        addNumericRow(
                rightX, rightY,
                "Lateral penalty",
                format(number("navLateralPenalty")),
                "nav_lateral_penalty_delta",
                0.10
        );
        rightY += 28;

        addNumericRow(
                rightX, rightY,
                "Congestion penalty",
                format(number("navCongestionPenalty")),
                "nav_congestion_penalty_delta",
                0.10
        );
        rightY += 28;

        addNumericRow(
                rightX, rightY,
                "Congestion radius",
                format(number("navCongestionRadius")) + " blocks",
                "nav_congestion_radius_delta",
                0.25
        );
        rightY += 28;

        addToggleRow(
                rightX, rightY,
                "Path evidence",
                bool("navPathEvidenceEnabled"),
                "toggle_path_evidence"
        );
        rightY += 28;

        addNumericRow(
                rightX, rightY,
                "Path node penalty",
                format(number("navPathNodePenalty")),
                "nav_path_node_penalty_delta",
                0.05
        );
        rightY += 28;

        addNumericRow(
                rightX, rightY,
                "Path residual penalty",
                format(number("navPathResidualPenalty")),
                "nav_path_residual_penalty_delta",
                0.05
        );
        rightY += 28;

        addNumericRow(
                rightX, rightY,
                "Max path residual",
                format(number("navPathMaxResidual")) + " blocks",
                "nav_path_max_residual_delta",
                0.25
        );
        rightY += 34;

        addRenderableWidget(
                Button.builder(
                        Component.literal("Navigation baseline"),
                        button -> SwarmControlClient.sendAction("nav_baseline", 0.0)
                ).bounds(rightX, rightY, 300, 20).build()
        );
    }

    private void addStatusRow(
            int x,
            int y,
            String label,
            String value
    ) {
        addRenderableWidget(
                Button.builder(
                        Component.literal(label + ": " + value),
                        button -> {
                        }
                ).bounds(x, y, 300, 20).build()
        );
    }

    private void addToggleRow(
            int x,
            int y,
            String label,
            boolean enabled,
            String action
    ) {
        addRenderableWidget(
                Button.builder(
                        Component.literal(label + ": " + (enabled ? "§aON" : "§cOFF")),
                        button -> SwarmControlClient.sendAction(action, 0.0)
                ).bounds(x, y, 300, 20).build()
        );
    }

    private void addNumericRow(
            int x,
            int y,
            String label,
            String displayValue,
            String action,
            double step
    ) {
        addRenderableWidget(
                Button.builder(
                        Component.literal("-"),
                        button -> SwarmControlClient.sendAction(action, -step)
                ).bounds(x, y, 28, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal(label + ": " + displayValue),
                        button -> {
                        }
                ).bounds(x + 32, y, 236, 20).build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal("+"),
                        button -> SwarmControlClient.sendAction(action, step)
                ).bounds(x + 272, y, 28, 20).build()
        );
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(
                font,
                title,
                width / 2,
                14,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                Component.literal(
                        "Server-authoritative live parameters  |  Swarm: "
                                + (bool("master") ? "§aENABLED" : "§cDISABLED")
                ),
                width / 2,
                27,
                0xB0B0B0
        );

        graphics.drawCenteredString(
                font,
                Component.literal("Changes apply immediately; use baselines to restore known-good defaults."),
                width / 2,
                91,
                0x909090
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void parseSnapshot(String snapshot) {
        values.clear();
        if (snapshot == null || snapshot.isBlank()) {
            return;
        }

        for (String part : snapshot.split(";")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || separator >= part.length() - 1) {
                continue;
            }

            values.put(
                    part.substring(0, separator),
                    part.substring(separator + 1)
            );
        }
    }

    private boolean bool(String key) {
        return Boolean.parseBoolean(values.getOrDefault(key, "false"));
    }

    private double number(String key) {
        try {
            return Double.parseDouble(values.getOrDefault(key, "0"));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String formatPercent(double value) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", value * 100.0);
    }
}
