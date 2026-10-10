# Swarm Mobs: Local Pack Movement Decongestion (Minecraft-only)

This experiment changes NPC **waypoint choice**, not damage, weapons,
explosions, vanilla combat permissions or player reach.

## Problem

Many Zombies approaching the same Minecraft player can converge on one
block even though the mod already assigns roles. Role names alone do not
guarantee distinct movement.

## Implemented behavior

When a Zombie has a **directly observed**, high-confidence, same-target
combat state, is assigned CHASER, and at least two of its local same-target
peers are within three blocks (with at least three peers total), it checks
two modest lateral destinations around its existing game waypoint.
The peer snapshot is the SAME set already found for the planner:
**no global squad commander and no extra entity search**.

It compares actual occupied game squares near each candidate
(1.75-block clearance) and chooses the less crowded side. A tie uses stable
formation-slot parity. A previously chosen side gets a small stability margin
and can only be switched after 20 ticks to avoid thrashing.

The selected waypoint deviates at most 2.5 blocks from the existing
destination. Before applying a diversion, the server checks the two proposed
game-block positions for loaded chunks, ground support, free headroom and
absence of obstructing fluid. If the preferred lane is blocked, the alternate
is attempted; if both are blocked, the **original destination remains**.
No additional world chunks are loaded and no extra entity scans are needed.
A real blocked location immediately overrides the 20-tick lane hold so that
an NPC does not persist with an obviously unusable diversion.
Minecraft's regular pathfinding and obstacle avoidance still determine whether
the rest of each route is reachable; this local check is not a guarantee of a
valid full path.

No effect when alone, with only one nearby peer, without a valid observation,
when division-of-labor/planner state isn't active, or for Spiders/Skeletons/
Creepers. Once a target changes, the old lateral lane is discarded.

## Verify in a disposable world

With the updated development build, run a normal Minecraft playtest world.
Create a group of Zombies around a valid Survival-mode player, and compare
the results with the same number of non-swarm vanilla Zombies.

```mcfunction
/swarmmobs inspect
/swarmmobs group
/swarmmobs panel
```

Inspect now reports `crowdLane` (0 / +1 / -1) and `crowdLaneSamples`.
The group command adds `localLaneDiverted`, `laneSamples` and
`blockedLaneFallbacks`. The Cloth Command Center Overview displays the same
server-sourced counters. A nonzero fallback count means a proposed
local diversion was rejected after the real game block checks.

These are actual movement **decisions**, not a claim that every chosen route
is reachable. Test obstacles, narrow corridors, mixed mob groups, solo mobs,
and target changes. Record whether the navigation reaches the destination,
not just whether the telemetry is nonzero.

## Automated tests

JUnit covers crowd recognition, left-vs-right lane choice, stable ties,
non-participating archetypes, disabled/invalid input, lane hold and resetting
on target change. Existing Minecraft GameTests remain required for
compile and regression verification.
