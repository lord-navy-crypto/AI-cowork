# Minecraft Swarm NPCs — multiple failed waypoint memory

## Problem

The previous navigation fallback retained only the most recent failed
Minecraft game waypoint for each Zombie or Skeleton. Two locally unreachable
side positions could therefore produce alternating failure loops:
A fails -> choose B -> B fails -> forget A -> choose A again.

The optional movement progress watchdog also used to compare a new
*planner destination* against the position of a mob still obeying an old
`PathNavigation.moveTo` command. Pending path-budget work could then be
mistaken for a stalled movement.

## Implemented fix

- Every Minecraft NPC keeps an independent, **fixed three-position**
  failed-waypoint memory for Skeleton shooting-position movements and Zombie
  near-melee flank movements. Memory is **per target** and reset as soon
  as the selected target changes.
- A Skeleton failure excludes nearby spots inside a two-block radius for
  **180 game ticks**. Zombie failure excludes a 1.5-block radius for
  **150 game ticks**. Entries expire independently; a repeated nearby failure
  refreshes the existing entry instead of evicting unrelated evidence.
- At most three recent failures are retained, with **constant space and
  O(3)** per-waypoint checks. No global squad commander, additional entity
  enumeration, persistent world saves, or new player position queries.
- Other verified candidate positions remain available, with existing
  terrain, same-target ally corridor, visibility and normal vanilla
  bow/melee rules preserved.
- The optional movement progress monitor only evaluates a planned point
  when it matches the **actual last-issued navigation command** within
  0.35 blocks. A newly issued command resets the progress observation
  window, preventing an old command's stale movement from counting as
  failure of the new one.

## Manual game acceptance

In a disposable Minecraft test arena, arrange two narrowly separated
blocked paths around a Zombie and Skeleton. After both attempted optional
positions fail, verify they don't endlessly alternate between just these
two squares. Keep a third usable square clear and confirm normal game
movement or native attacks can continue.

Check `/swarmmobs inspect` and `/swarmmobs group` alongside navigation
status, failed-movement fallback counters and Command Center. Repeat with
the player changing location: changing a target clears the previous
failure evidence; changing the destination without an issued path should
not cause an immediate no-progress counter increment.

Java JUnit tests prove fixed-memory behavior, independent expiry, duplicate
refresh, invalid data rejection, target reset and issued-path matching.
Existing Minecraft Runtime GameTests guard against regressions. These
tests alone do **not** prove flawless real-world gameplay navigation across
all Minecraft biomes or dense packs.
