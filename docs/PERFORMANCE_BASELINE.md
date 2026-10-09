# Swarm Mobs — performance baseline for v0.13

This file defines a reproducible performance experiment. It does **not** claim
measured speedups from the neighbor/congestion refactor; those must be measured.

## Test matrix

For each test, compare both Swarm enabled and disabled, using the same world,
difficulty, entity distribution, render distance, simulation distance and seed.

| Entities | Scenarios |
| ---: | --- |
| 10 | open field, dense walls, small base |
| 50 | open field, dense walls, small base |
| 100 | open field, dense walls, small base |
| 200 | open field, dense walls, small base |

Capture 3 independent runs per row after warmup.

## Measurements

- Mean / p95 server MSPT and whether sustained TPS drops below 20
- CPU profile (including `SwarmMobEvents.onEntityTick`,
  `SwarmApproachGoal.localObstacleAvoidance`,
  `chooseRecoveryWaypoint` and Minecraft PathNavigation)
- Number of path queries, obstacle detours and recovery failures from the
  built-in experiment telemetry
- Observed navigation correctness, combat handoff, engineering tasks and
  communication delivery under identical scenarios

## Profile collection

Use Minecraft's debug profiler (F3 + L) when supported in the test environment.
Preserve profile archives for before/after comparison.

Do not estimate speedups from successful CI alone; CI tests validate behavior,
not frame-time, MSPT, or load scaling.

## Performance design in this branch

- One entity-index query per swarm planning cycle supplies movement and
  communication peers. Each channel still applies its own radius and cap.
- One conservative entity-index query per obstacle/recovery planning episode
  supplies positions for all candidate congestion scores.
- An expanded geometry envelope fixes previously missed peers near far-side
  candidate waypoints.

## Next optimization gate

Measure path creation cost before imposing new per-tick budgets. Global budgets
must be fair between mobs, avoid path-starvation, and preserve combat/engineering
fallback. Never run live world/pathfinding access off the server thread.

## Path evidence budget (next v0.13 increment)

- The server-level option `navPathEvidenceBudgetPerTick` defaults to 96
  **reserved candidate slots per dimension per game tick** (8..512).
- Obstacle avoidance reserves up to four evidence queries atomically; recovery
  reserves up to six. A blocked candidate consumes a reserved slot but does
  not execute an expensive `createPath`, so reserved tokens are an upper bound
  on explicit evidence queries rather than a direct CPU time measurement.
- When an episode cannot reserve its full candidate set, the whole episode is
  deferred. It does not assert false feasibility, inflate recovery failure
  metrics, or execute an unbounded partial set.
- FIFO waiting priority prevents newly arriving agents from indefinitely
  bypassing already deferred agents. Inactive waiter entries expire.
- The Cloth Navigation tab displays this tick's reservations, queue length,
  and lifetime grant/deferral counters; the existing experiment Path Queries
  field still tracks actual explicit evidence calls.
- This quota does **not** cover vanilla path work initiated by
  `PathNavigation.moveTo` or other Minecraft AI goals. Those require
  separate profiling before attempting to throttle them.
