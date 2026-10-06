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

Active stable line: `main` through v0.5.6; current integration work: `swarm-sensing-faults-v0.6.0`

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
- runtime telemetry, particle debugging, JUnit coverage, and Minecraft GameTest-server integration tests;
- external AI remains reserved and disabled while deterministic baselines are developed.

Development commands:

```text
/swarmmobs status
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

**Swarm Mobs v0.5.6 is merged on `main`; v0.6.0 deterministic sensing-fault injection is under integration.**

The old AI CoWork implementation is no longer developed on `main`. Use the archive branch above if historical AI CoWork source is needed.
