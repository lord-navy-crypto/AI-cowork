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
- Obstacle avoidance probes four local candidate positions and recovery probes
  up to six. **Only terrain-passable candidates** reserve path-evidence slots;
  already-blocked candidates never consume quota or call `createPath`.
  A fully blocked candidate set also skips its congestion entity scan.
- Reservations still happen atomically for all candidate positions requiring
  evidence; tokens are an upper bound on explicit `createPath` calls and not
  a direct measurement of CPU time.
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

## Navigation command deduplication

The `SwarmApproachGoal` still refreshes its local tactical state, but it
avoids issuing a redundant `PathNavigation.moveTo` when the commanded
position and movement speed are effectively unchanged.

- Significant destination changes (>0.25 horizontal blocks or >0.5 Y blocks)
  and speed changes (>0.05) issue a fresh command immediately.
- When navigation reports completion (including a failed path), unchanged
  destinations are retried no more frequently than every 8 ticks.
- While an existing path is active, unchanged destinations refresh at least
  every 20 ticks to account for dynamic terrain.
- Starting a new SwarmApproachGoal always issues a fresh movement request.
- Combat handoff remains under the same existing GoalSelector conditions.
- The Cloth Navigation tab now reports navigation commands issued/skipped,
  completed-path retries and active-path periodic refreshes. These are
  behavioral diagnostics and do not directly measure milliseconds saved.

Benchmark before/after with the same moving-player trajectory and a blocked
wall case. A change in path-call counts alone does not prove higher TPS; verify
that it does not delay reactive steering or trapped-mob recovery.

## Budget-denial navigation correctness (follow-up fix)

- A denied obstacle-evidence reservation now defers the **entire movement
  update** instead of falling back to a new unverified direct route through
  the blocked forward probe. An already-active path is left untouched.
- Recovery budget deferrals remain distinct from recovery failures.
- A waiting mob cancels its queued demand when its obstacle disappears,
  all candidate locations are terrain-blocked (no path evidence needed), its
  recovery set is empty, or its movement goal is stopped.
- The Runtime GameTest intentionally saturates the evidence budget in front
  of a real block wall and verifies that denial cannot issue a new movement
  command or publish fabricated path feasibility data.

The queue cancellation protects other mobs' reserved capacity; it is not a
Minecraft vanilla-pathfinding rate limit. If no existing path is active, the
agent can stay still until quota becomes available on a subsequent tick.

## Target-scoped tactical coordination

Physical neighbors are not automatically tactical teammates when multiple
players are present. We now split the local peer set:

- **Physical neighbors**: all supported nearby peers continue to participate
  in collision-aware steering (separation, cohesion and alignment).
- **Communication neighbors**: all in-range peers may still relay observations,
  with existing latency/dropout/sensing rules unchanged.
- **Tactical squad**: only peers whose current remembered target UUID matches
  this agent's selected target UUID influence capability-based formation slots,
  composition/breacher standoff, fire-support lane selection, role/task
  saturation, and same-capability search sector counts.

Null target UUIDs never form squads. This avoids a Skeleton shifting its
support lane due to a nearby Creeper attacking a *different* player. It also
keeps role assignments from another player's unrelated fight from crowding
the squad's allocation.

This does **not** prevent target observations from moving between local
agents: the ordinary message pipeline may still cause a mob to switch
targets, at which point it joins the new target's local tactical squad.

## Live dual-player tactical isolation regression

The `swarm_runtime_two_player_squads` Minecraft GameTest now creates
**two independent fake players** (unique usernames) plus live Skeleton,
Zombie, and Creeper entities. It checks two consecutive real planning phases:

1. Skeleton and Zombie initially perceive player A while Creeper perceives
   player B. The Skeleton's tactical peer count must be one, with **zero**
   same-target breachers, even though Creeper is a nearby physical neighbor.
   Creeper initially has zero same-target allies.
2. Player B becomes Spectator without leaving the loaded GameTest region.
   All three monsters should select the remaining eligible player A, and the
   resulting tactical squad should contain two peers per member. Skeleton
   should then report one same-target Creeper breacher.

Movement-speed zero keeps each member positioned consistently for sensing
and target-composition assertions. Live `EntityTickEvent.Post` planning,
attachments, fake player validity and actual entity neighborhoods are used.

Runtime Command Center -> Coordination now reports:
- `liveTacticalAlliedAgents`: count of live swarm members who currently
  have at least one **same-target** local peer.
- `liveTacticalPeerLinks`: sum of directed local same-target peer observations
  (a pair observed in both directions counts as two).
- `liveTacticalBreacherSupport`: number of members with at least one
  same-target nearby breacher; different-target Creepers do not count.

This diagnostic is not an absolute count of disconnected squads: agents
sharing one target far apart might belong to different local subgroups.
