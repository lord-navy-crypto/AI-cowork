# Swarm Mobs — Navigation episode invalidation

This update fixes a Minecraft NPC movement bug in the actual
`SwarmApproachGoal`: a Goal instance can remain active while its
server-authoritative swarm target changes or it transitions between
`SEARCH` and `ENGAGE`. Previously, a local obstacle detour or stuck
recovery chosen for the *previous* context could remain in force until its
timer expired, making the mob look unresponsive even though the next
planner destination was already correct.

## What changed

- The running movement Goal now tracks its current target UUID and
  `SwarmBehaviorMode`. A target switch or SEARCH/ENGAGE transition
  cancels its current obstacle detour, stuck-recovery override and stale
  PathNavigation command, then allows normal planning for the new context.
- While in ENGAGE with a *direct* target observation, a recovery built
  against an old plan is cancelled if the newest game waypoint has moved
  more than **6 blocks** from its original anchor. Small target movement
  does not invalidate recovery.
- SEARCH rotating sectors are exempt from the six-block check: sector
  rotation must not cause needless per-frame path churn.
- The ordinary combat move/attack handoffs, path budget, navigation
  cooldown, terrain checks and default player-target exclusions stay in
  place. No new damage or attack mechanic is introduced.
- `staleRouteResets` is a real cumulative per-agent counter, now
  displayed in the server-fed Command Center and both
  `/swarmmobs inspect` and `/swarmmobs group`.

## Reproduce and observe

Use a disposable Minecraft survival test world. Spawn a mixed pack with
`/swarmmobs debug spawnmixed 12`. Let them obtain a normal game target,
break line of sight behind a wall to force SEARCH, then emerge again on
a different side of the arena. Run:

```mcfunction
/swarmmobs inspect
/swarmmobs group
/swarmmobs panel
```

Observe that mobs are not held to old recovery/obstacle waypoints during
SEARCH/ENGAGE changes. Inspect `navMode`, `navWaypoint`,
`staleRouteResets` and `searchRallyActive`. Repeat near different
types of Minecraft obstruction.

Note: one counter increment represents one actually invalidated movement
context, not a guarantee that a route reached its destination. The
existing full pathfinder still evaluates whether alternate paths exist.

## Test boundaries

The new pure policy JUnit regression tests cover target IDs, behavior
transitions, first-run initialization, direct observed movement threshold,
SEARCH immunity and non-finite values; the state telemetry counter test
checks that resets aren't invented by a mere target assignment.
Minecraft Runtime GameTests and the full build must pass before merging.
Real-world playtesting still determines how natural the animation and
navigation feel over longer scenarios.
