# Initial roadmap

## Phase 0 — project bootstrap
- choose Minecraft version and mod loader;
- establish reproducible Gradle build;
- add CI compile/test workflow;
- define package layout and simulation/debug conventions.

## Phase 1 — deterministic swarm baseline
- neighbor discovery;
- separation / alignment / cohesion;
- obstacle-aware movement;
- swarm identifiers and configurable parameters;
- debug overlay for local neighbors and current steering terms.

## Phase 2 — distributed coordination
- local target memory;
- communication radius;
- message age / confidence;
- simulated latency and packet loss;
- fallback behavior when communication is unavailable.

## Phase 3 — roles and task allocation
- scout / follower / support / reserve-style roles;
- dynamic task assignment;
- centralized vs. decentralized coordination modes;
- metrics for completion time, coordination cost, and failure recovery.

## Phase 4 — player-controlled swarm
- command device / control interface;
- high-level commands such as follow, guard, explore, collect, and return;
- autonomous decomposition of high-level commands into local tasks.

## Phase 5 — learning experiments
Only after deterministic baselines are measurable:
- collect behavior traces;
- compare hand-designed policies with learning-based policies;
- experiment with offline or reinforcement-learning approaches where they add measurable value.
