# SWARM-MOBS Engineering Roadmap

SWARM-MOBS is developed as a deterministic, inspectable multi-agent / swarm-robotics experiment inside Minecraft, not merely as a difficulty mod.

## Engineering contract

Every major subsystem should be:

- **decentralized where practical** — agents act from local state and neighbor information rather than omniscient shared truth;
- **bounded** — prediction, steering, recovery, search, and faults have explicit limits;
- **deterministic for experiments** — the same state and experiment seed reproduce the same fault decisions;
- **observable** — important state is exposed through commands, telemetry, particles, or tests;
- **diagnosable** — failures should be distinguishable between perception, communication, coordination, planning, and navigation;
- **testable** — pure policies get JUnit coverage and runtime integration is gated by Minecraft GameTests;
- **safe to extend** — external AI must remain optional and have a deterministic fallback.

## System pipeline

```text
environment
   ↓
perception / sensing
   ↓
local target memory
   ↓
neighbor communication
   ↓
coordination / role allocation
   ↓
ENGAGE or SEARCH planning
   ↓
local terrain/navigation layer
   ↓
movement / vanilla combat handoff
   ↓
telemetry / feedback
```

## Completed baseline

### v0.3.x — heterogeneous cooperation
- Zombie / Skeleton / Spider capability profiles
- role-aware formation behavior
- composition-aware responsibility sharing
- separation / cohesion / alignment
- formation-slot hysteresis

### v0.4 — decentralized SEARCH
- truthful last-known target snapshots
- confidence decay
- transition from ENGAGE to SEARCH
- capability-banded rotating search sectors

### v0.5 — bounded target prediction
- observed target velocity snapshots
- confidence-weighted short-horizon lead
- hard horizon and distance caps
- prediction disabled during SEARCH

### v0.5.1 — behavior-aware movement
- stale ENGAGE slowdown
- independent SEARCH coverage speed

### v0.5.2 — stuck recovery
- deterministic progress-based stuck detection
- bounded lateral recovery waypoint

### v0.5.3 — proactive obstacle steering
- front / left / right local terrain probes
- deterministic lateral detour choice

### v0.5.4 — detour hysteresis
- temporary detour commitment
- early release on waypoint arrival

### v0.5.5 — navigation observability
- PLAN / OBSTACLE_DETOUR / RECOVERY telemetry
- actual navigation waypoint telemetry
- detour and recovery counters

### v0.5.6 — walkability-aware terrain probes
- reject unsupported detours near pits and ledges
- configurable small accepted drop
- water-aware support checks

## Current milestone — v0.6 sensing imperfections

Goal: stop treating direct perception as a perfect sensor.

Planned/implemented baseline:
- deterministic direct-observation dropout;
- bounded horizontal position noise;
- experiment seed for exact reproducibility;
- sensing accepted/dropped counters;
- last injected sensing-error telemetry;
- runtime commands for baseline/fault experiments;
- default baseline remains perfect sensing so normal gameplay is unchanged.

This milestone enables experiments such as:
- perfect sensing + perfect communication;
- noisy sensing + perfect communication;
- perfect sensing + lossy communication;
- noisy sensing + lossy communication;
- recovery/search performance under combined faults.

## Control and experiment interface — v0.6.1

The in-game control panel provides a server-authoritative interface for live tuning without requiring command memorization.

Initial pages:
- Sensing
- Communication
- Search & Prediction
- Navigation

Panel actions are sent to the server, permission-checked, applied to the live config, and echoed back as a fresh snapshot. Known-good baselines remain available per subsystem and globally.

## Next milestones

### v0.7 — stronger task allocation and local mission logic
Current implementation:
- explicit stable tactical-role state per agent;
- pending-role state before a reassignment is committed;
- configurable role hysteresis to prevent rapid role thrashing;
- reassignment counters and group telemetry;
- live Coordination controls in the in-game panel.

Next within v0.7:
- deeper capability-loss redistribution beyond the current local composition policy;
- mission-level responsibilities that persist across ENGAGE / SEARCH transitions;
- role-balance metrics and explicit responsibility coverage checks.

### v0.8 — terrain-aware local planning
Current implementation:
- four deterministic short-horizon detour candidates instead of binary left/right choice;
- collision and walkability feasibility filtering for every candidate;
- weighted forward-progress scoring;
- lateral-detour penalty;
- local swarm-congestion penalty to reduce same-side crowding;
- live Navigation-page tuning for local-planner weights.

Current v0.8.1 extension:
- optional Minecraft PathNavigation path creation for each terrain-feasible local candidate;
- reject candidates whose generated path cannot reach the candidate target;
- penalize longer node sequences and residual path distance;
- bound the extra pathfinding work to at most four candidates and only after a confirmed front obstruction;
- live Navigation-page controls for path evidence and path penalties.

Current v0.8.2 extension:
- replace single parity-based recovery waypoint with six deterministic escape candidates;
- include forward-side, pure-side, and backward-side recovery options;
- validate recovery candidates with collision, walkability, local congestion, and optional PathNavigation evidence;
- score recovery candidates with the same bounded local cost function as obstacle detours;
- record recovery-planner attempts and failures;
- live control-panel tuning for stuck window, minimum progress, recovery distance, and recovery duration.

Current v0.8.3 extension:
- record whether the latest local plan decision came from OBSTACLE_DETOUR or RECOVERY;
- expose total, blocked, unreachable, and feasible candidate counts;
- expose selected candidate index and selected score;
- accumulate PathNavigation evidence query counts;
- show planner diagnostics in inspect/group telemetry;
- add a dedicated Runtime GameTest that forces an immobile Zombie through stuck detection and asserts recovery planning and recovery commitment.

v0.8 is now a complete bounded local-planning baseline. Future work moves into v0.9 experiment/evaluation infrastructure.

### v0.9 — experiment and evaluation layer
Current v0.9.0 implementation:
- server-authoritative repeatable experiment presets;
- BASELINE, NOISY_SENSING, LOSSY_COMMS, COMBINED_FAULTS, and NAVIGATION_STRESS conditions;
- one shared experiment seed propagated to sensing and communication fault models;
- Experiment page in the live control panel;
- command-line preset and seed controls;
- automatic CUSTOM state when parameters are manually tuned after applying a preset.

Current v0.9.1 extension:
- Experiment Start / Reset / Snapshot lifecycle;
- run-relative metric baselines without clearing existing agent telemetry;
- aggregate communication accepted/delivered/dropped deltas;
- observed communication drop ratio;
- obstacle-detour and recovery counts;
- recovery-planning attempts, failures, and failure ratio;
- PathNavigation evidence query count;
- tactical role reassignment count;
- live Experiment-page metric display and command-line snapshot output.

Current v0.9.2 extension:
- per-agent SEARCH episode lifecycle telemetry;
- same-target direct reacquisition success counting;
- SEARCH failure counting when target memory expires;
- reacquisition latency in ticks, aggregated run-relative;
- search success rate over completed episodes;
- active SEARCH episode count;
- command and Experiment-panel exposure;
- Runtime GameTest that enters SEARCH, restores line of sight, and verifies direct reacquisition metrics.

Next within v0.9:
- coverage efficiency;
- normalized communication overhead per agent/time;
- role imbalance;
- exportable metric snapshots.

### v1.0 deterministic research baseline
A stable non-AI swarm system that can be evaluated under imperfect sensing, imperfect communication, heterogeneous agents, target loss, and complex terrain.

## Later: optional local AI / Ollama

External AI is intentionally **not** the core control loop yet.

When introduced, it should:
- be optional;
- run locally/private where possible;
- operate at a higher strategy/explanation layer rather than replacing bounded low-level safety/navigation;
- expose model/runtime state;
- never fabricate experiment outcomes;
- fall back to deterministic policies when unavailable or uncertain.

The deterministic baseline must remain independently playable, measurable, and testable.


## v0.10 — local AI strategy layer

### v0.10.0 AI Shadow Mode

Current implementation:
- local-only Ollama endpoint with loopback validation;
- asynchronous Java 21 HTTP so inference never blocks the Minecraft server thread;
- structured JSON-schema strategy output;
- aggregate v0.9 experiment telemetry as model input;
- bounded strategy modes and sanitized multipliers;
- explicit deterministic fallback;
- AI OFF by default;
- Shadow Mode records recommendations only and cannot alter gameplay;
- commands for provider status, model listing/selection, enable/disable, and one-shot shadow inference;
- in-game AI Shadow panel with latency/provider/rationale/fallback/error telemetry.

Next within v0.10:
- repeated/event-driven shadow sampling with rate limits;
- AI-vs-deterministic experiment comparison metrics;
- only after validation, an optional bounded active strategy adapter with TTL/hysteresis and deterministic fallback.
