# Minecraft Swarm NPC — PathNavigation command acceptance

## The real bug

The movement Goal previously set `commandIssued=true` and updated its
last-issued destination **before calling**
`mob.getNavigation().moveTo(x,y,z,speed)`. That Minecraft API returns
a boolean. A locally open game square is not proof of a complete path:
`moveTo` can reject its route.

As a result, rejected destinations could be mistakenly treated as active
movement commands in the short-waypoint progress watchdog, and be retried
needlessly. Optional Skeleton sidesteps and Zombie near-melee flanks could
temporarily occupy movement priority even when Minecraft had no route.

## Changes to actual game code

- PathNavigation's returned **boolean** is now authoritative. The Goal
  records an issued command and its coordinates **only if moveTo accepted**.
- On rejection, it marks the command not issued, clears stale navigation
  state, increments `navigationCommandRejections`, and avoids using that
  nonexistent route as evidence for movement progress.
- If the rejected route is an *active optional* Skeleton spacing or Zombie
  short-flank segment, that segment immediately expires through its
  existing per-target failed-waypoint memory and cooldown. This makes
  the normal Minecraft bow or melee Goal eligible, subject to the existing
  visibility and same-target ally-shot restrictions.
- On the next attempt, an **unchanged rejected destination** is retried
  no sooner than 12 game ticks. A meaningfully changed (>=0.5 block)
  candidate bypasses the wait. This keeps the default normal pursuit
  running and avoids hammering the pathfinder on a static obstruction.
- The short rejection backoff remains in the Goal across ordinary
  stop/start handoffs; it resets on a new target or SEARCH/ENGAGE phase.
  No new global entity scans or forced chunk loading.

## Inspect actual outcomes

In a disposable test world, arrange a clear current attack corridor but
an unreachable optional sidestep. Run:

```mcfunction
/swarmmobs inspect
/swarmmobs group
/swarmmobs panel
```

Observe the new server counters:

- `gamePathRejects`: total rejected Minecraft movement submissions.
- `skeletonPathRejects`: rejected optional Skeleton game repositioning.
- `zombiePathRejects`: rejected optional Zombie game flank movement.
- `skeletonNoProgress`, `zombieNoProgress`: distinct and narrower
  counters for **accepted paths** that failed to close the waypoint gap.

Expected: a rejected optional move should not lock out an otherwise
legal native bow/melee; the mob should not issue the exact same blocked
route on every tick; changing its current destination should trigger
a new attempt. Ally-blocked arrows remain blocked for safety.

Pure unit tests verify retry timing, candidate changes, target/clock
reset, invalid coordinates and attack-state failure attribution. The
existing Minecraft Runtime GameTests include the vanilla Skeleton bow
handoff and mixed-mob interactions. Automated tests do not replace
manual playtesting of complex game terrain.
