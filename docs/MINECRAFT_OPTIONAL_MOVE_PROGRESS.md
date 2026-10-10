# Minecraft NPC optional-waypoint progress detection

This changes **movement behavior in a Minecraft mod**. It does not alter
damage, arrows, explosions, targeting, or any real-world tactics.

## Problem in the previous system

A Skeleton can choose a nearby, supported game square for improved
shooting position, and a Zombie can choose a short side waypoint before
returning to normal melee. A local block check does not prove that Minecraft
PathNavigation can reach the destination. Previous timeouts (Skeleton:
30 ticks; Zombie: 18 ticks) would eventually restore native combat, but
could spend much of the available movement lease walking in place or
circling without getting any closer.

## New behavior

In a running `SwarmApproachGoal`, an optional Skeleton or Zombie
movement segment is now sampled in game ticks using **the actual distance
between that mob and its planned waypoint**:

- The first sample establishes a baseline, with no extra entity scan or
  global pathfinding request.
- After **12 ticks**, the mob must have closed the remaining distance
  by at least **0.25 game blocks**. A changed destination (>=1.5 blocks)
  causes a fresh baseline rather than a spurious failure.
- If PathNavigation says its path is already done but the mob remains
  >1.2 blocks away, the optional movement can fail after **6 ticks**.
- A reached waypoint does not count as a failure.
- When an optional movement stalls, it is ended immediately. Its failure
  is recorded using the **same per-target failed-square memory and
  retry cooldown** as the normal timeout. The normal vanilla bow/melee
  handoff is restored as applicable. The general obstacle/detour recovery
  routine is not disabled.
- The check runs only while an actual optional game movement lease is
  active. SEARCH, regular pursuit, engineering workers and Nest expansion
  do not receive these new timeouts.

For diagnosis, `/swarmmobs inspect`, `/swarmmobs group` and the
server-backed Command Center show the *subset* of movement fallbacks
that came from measured lack of progress:
`skeletonNoProgress` and `zombieNoProgress`.

## Manual acceptance tests

Use a disposable world and real Survival-mode player targets.

1. Put a Skeleton on a narrow platform near an attractive but
   path-inaccessible shooting square. Observe that it does not hold MOVE
   indefinitely; inspect `skeletonNoProgress`, then confirm it can still
   fire its ordinary bow once the valid shot corridor is clear.
2. Give a Zombie a side waypoint around a wall. Watch the actual in-game
   movement: if it can't approach its position, inspect
   `zombieNoProgress` and confirm the normal melee approach returns.
3. Repeat on open terrain. Genuinely progressing mobs should not
   prematurely abort their planned side movements.
4. Move the player far enough to change the planned game waypoint while
   the mob is repositioning. A changed waypoint must start a new
   progress sample, not be mislabeled as a failed path.
5. Repeat with ordinary SEARCH, crafting/hauling mobs and solo monsters.
   The new progress watchdog must have no effect on these modes.

Pure JUnit tests validate the time windows, measured improvement,
reset on a new waypoint, no false failures at arrival and non-finite
inputs. Existing Minecraft Runtime GameTests guard compatibility but
do **not** prove long-term animation or route reliability in all terrain.
