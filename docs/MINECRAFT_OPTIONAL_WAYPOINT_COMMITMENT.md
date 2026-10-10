# Minecraft NPC short-waypoint commitment and local Y

## Why this matters

The Skeleton spacing planner previously recomputed an outward 3-block
candidate from the **current Skeleton position** on every planning update.
A mob already walking to an earlier waypoint could continually receive
slightly shifted destinations, causing redundant path refreshes and
confusing progress-based navigation diagnosis.

Separately, the movement Goal normally uses the directly observed
player's **Y height** for the destination. That is useful for chasing a
player, but a local Skeleton sidestep or Zombie near-melee flank is a
game square near the **mob's feet**, not a teleport onto a raised player
platform.

## Implemented behavior

- For an active Skeleton optional movement, compare its **existing** game
  waypoint with a newly proposed one. Keep the existing point only while
  it is 0.85–4.5 blocks from the Skeleton, the newly considered point is
  within 2.25 blocks of it, the direct player observation remains reliable,
  and the actual old square still has support/clearance, an ally-clear
  corridor and an unobstructed shot. This uses the same locally sampled
  peer positions; no extra entity scan or new PathNavigation query.
- A new angle, obstruction, arrived endpoint, missing visual evidence or
  changed proposal outside the window permits normal replanning. Previous
  failed-waypoint memory, movement timeouts and bow attack handoff stay in
  force.
- When optional Skeleton spacing or Zombie near-melee flank movement is
  actively selected, PathNavigation receives the **mob's local Y**, not
  the player's Y. Ordinary pursuit, search and movement toward a target
  continue using their prior height logic.

## Acceptance checks

In a disposable Minecraft game world, first test a Skeleton approached by
a visible Survival-mode player on flat terrain. Confirm that it does not
change destination every planner frame while moving. Then place a player
on a raised platform and test a Skeleton and flank Zombie on lower ground:
the local sidestep should seek a nearby supported square, not the
height of the player's raised block.

Run `/swarmmobs inspect`, `/swarmmobs group`, and `/swarmmobs panel`.
Watch navigation waypoint and optional move state as well as fallback
counters. Repeat with the line of sight blocked to verify that native bow
or melee handoff is not delayed by an out-of-date optional waypoint.

JUnit checks hold/arrival/large angle/invalid sightings and Y selection;
existing Minecraft Runtime GameTests guard the real combat Goal bridge.
This does not prove every path is reachable on irregular terrain; the
PathNavigation system remains authoritative.
