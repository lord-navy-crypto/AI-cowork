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
