# Swarm AI for Minecraft — development repository

This repository has been repurposed for a new **Minecraft multi-agent / swarm-intelligence project**.

The previous **AI CoWork** project has been preserved in two places:

- OpenPenguin integration branch: `lord-navy-crypto/Openguin@ai-cowork-integration`
- Archive branch in this repository: `archive/ai-cowork-2026-10-05`

## Project direction

The new project will explore coordinated agent behavior inside Minecraft, with ideas drawn from swarm robotics, distributed systems, multi-agent planning, and AI.

Planned directions include:
- local sensing and neighbor discovery;
- cohesion, separation, alignment, and obstacle avoidance;
- distributed communication between agents;
- role assignment and task allocation;
- shared or partially shared target memory;
- configurable communication delay, packet loss, sensing noise, and failures;
- swarm-state visualization and debugging;
- player-controlled or player-tamed swarms for exploration, transport, defense, and other tasks;
- later experimentation with learning-based policies once deterministic baselines are stable.

## Engineering principle

The goal is not only to make mobs "smarter." The project should expose the underlying mechanism:

```text
perception
   ↓
local communication
   ↓
coordination / task allocation
   ↓
decision
   ↓
movement / action
   ↓
feedback
```

The system should make these stages observable and tunable so the repository can also function as a small multi-agent experimentation platform.

## Current development status

Active stable line: `main` through v0.9.2; current integration work: `swarm-ai-shadow-v0.10.0`

The current playable system includes:

- heterogeneous Zombie / Skeleton / Spider swarm agents with capability-aware tactical roles;
- local separation, cohesion, alignment, steering caps, and formation-slot hysteresis;
- explicit neighbor-to-neighbor target communication with configurable radius, latency, deterministic packet loss, and experiment seeds;
- truthful last-known target snapshots with confidence decay instead of hidden live tracking;
- decentralized ENGAGE → SEARCH transitions and rotating capability-banded search sectors as uncertainty grows;
- bounded observed-motion prediction during ENGAGE, with prediction cleared during SEARCH;
- behavior-aware movement: stale ENGAGE information can slow pursuit while SEARCH keeps an independent configurable coverage speed;
- local stuck detection with deterministic left/right recovery waypoints for blocked navigation in complex terrain;
- short-range terrain probes that preemptively steer around immediate obstacles before a full stall occurs;
- obstacle-detour hysteresis that holds a chosen bypass briefly, reducing left/right steering jitter near wall edges;
- navigation-layer telemetry exposing the actual PLAN / OBSTACLE_DETOUR / RECOVERY waypoint and cumulative recovery counters;
- walkability-aware local probes that reject unsupported detours near pits and ledges while allowing a configurable small drop;
- optional deterministic direct-sensing dropout and bounded horizontal observation noise for reproducible fault-injection experiments;
- a server-authoritative in-game control panel for live Experiment, Sensing, Communication, Coordination, Search/Prediction, and Navigation tuning;
- bounded tactical-role reassignment hysteresis with pending-role and reassignment telemetry;
- multi-candidate local navigation scoring that combines terrain feasibility, forward progress, lateral cost, and nearby swarm congestion;
- optional Minecraft PathNavigation reachability/path-cost evidence for local detour candidates;
- multi-direction stuck recovery planning that validates six escape candidates with terrain, congestion, and path evidence before committing;
- explainable local-planner telemetry for candidate counts, blocked/unreachable filtering, selected index/score, and cumulative PathNavigation query count;
- experiment snapshots with communication, navigation, recovery, role-reassignment, SEARCH-success, and reacquisition-latency metrics;
- optional local Ollama AI Shadow Mode that reads aggregate telemetry and emits bounded structured strategy recommendations without changing gameplay;
- runtime telemetry, particle debugging, JUnit coverage, and Minecraft GameTest-server integration tests;
- external AI remains reserved and disabled while deterministic baselines are developed.

Development commands:

```text
/swarmmobs status
/swarmmobs panel
/swarmmobs experiment baseline|noisy_sensing|lossy_comms|combined_faults|navigation_stress
/swarmmobs experiment seed <value>
/swarmmobs experiment start
/swarmmobs experiment reset
/swarmmobs experiment snapshot
/swarmmobs inspect
/swarmmobs group
/swarmmobs debug spawn <count>
/swarmmobs debug particles on|off|toggle
/swarmmobs debug sensing baseline
/swarmmobs debug sensing drop <0..1>
/swarmmobs debug sensing noise <blocks>
/swarmmobs debug sensing seed <value>
```

The repository roadmap is versioned in `ROADMAP.md`. The development branch also includes a zero-dependency particle debugger. When enabled, vanilla particles show planned destinations, a small subset of local neighbor links, and the current target marker directly in the world.

The GitHub Actions pipeline compiles against Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21, runs deterministic JUnit tests, and gates the swarm branch with real Minecraft GameTest-server runtime tests.

## Status

**Swarm Mobs v0.9.2 is merged on `main`; v0.10.0 local Ollama AI Shadow Mode is under integration.**

The old AI CoWork implementation is no longer developed on `main`. Use the archive branch above if historical AI CoWork source is needed.


## Local AI Shadow Mode

v0.10 introduces an optional local-only Ollama strategy advisor. It is **OFF by default** and **never applies recommendations to movement, combat, navigation, or swarm parameters**.

Commands:

```text
/swarmmobs ai status
/swarmmobs ai models
/swarmmobs ai model <name>
/swarmmobs ai on
/swarmmobs ai off
/swarmmobs ai shadow
```

The in-game control panel includes an **AI Shadow** page showing the last recommendation, provider, latency, bounded multipliers, rationale, fallback count, and error count.

See `docs/OLLAMA_SHADOW.md` for architecture and safety boundaries.
