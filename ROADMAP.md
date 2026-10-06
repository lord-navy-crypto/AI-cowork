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

## Next milestones

### v0.7 — stronger task allocation and local mission logic
- explicit per-agent responsibility state;
- local reassignment when one capability disappears;
- bounded role switching and anti-thrashing;
- metrics for role balance and reassignment count.

### v0.8 — terrain-aware local planning
- richer candidate scoring beyond binary left/right probes;
- pathability/reachability-aware local candidate selection;
- terrain cost / congestion evidence;
- improved recovery candidate validation.

### v0.9 — experiment and evaluation layer
- reacquisition time;
- search success rate;
- coverage efficiency;
- communication overhead;
- role imbalance;
- obstacle-detour/recovery rate;
- repeatable experiment presets.

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
