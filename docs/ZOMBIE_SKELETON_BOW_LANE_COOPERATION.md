# Minecraft Swarm — Zombies yield clear lines to Skeleton teammates

## Motivation

Previously, same-target Skeleton mobs could detect allied Zombies or Creepers
blocking a visible player's arrow corridor and try to relocate themselves.
However, a Zombie's own game movement planner did not respond to this
cross-species constraint, so a group could repeatedly crowd the Skeleton's
firing line even when a nearby playable side route existed.

This update modifies **Minecraft NPC positions only**. It never changes
arrows, damage, real-world tactics, aggression, targets, or access to any
unseen player coordinates.

## Live game behavior

When a Zombie is still more than **4.5 blocks** from a directly visible
Survival-mode player, and local division of labor is enabled:

1. Reuse its already collected **same-target** nearby NPC snapshot.
   Consider up to four observed Skeleton teammates that themselves have
   current line of sight and are within their standard 15-block bow
   envelope. This does not perform another world entity scan.
2. If the Zombie's existing *planned game waypoint* already avoids
   those active Skeleton corridors, **do nothing**. Near-melee Zombies,
   unavailable observations and engineering/material workers are excluded.
3. If its planned waypoint would obstruct a nearby Skeleton's direct
   visible game corridor, propose two bounded **2.5-block lateral**
   alternatives, perpendicular to that Skeleton-to-player line.
4. Check both alternatives against the actual loaded Minecraft terrain,
   solid footing and clearance, and **all observed nearby Skeleton shot
   corridors**. Pick an eligible side deterministically with its stable
   formation slot; if neither is valid, preserve the original waypoint.
5. Preserve native zombie melee handoff, ordinary obstruction/recovery,
   path acceptance and progress monitoring. This is a proposed route,
   not a teleport or guaranteed path to the game player.

## Server-truth diagnostics

`/swarmmobs inspect`, `/swarmmobs group`, and the Command Center now show:

- `zombieBowLanePlanned`: how many zombies currently have a locally
  terrain-checked suggestion to move out of a teammate's arrow corridor.
- `zombieBowLaneAccepted`: cumulative **actual PathNavigation-accepted
  direct MOVE commands** for those suggestions. Merely planning a route,
  a rejected PathNavigation request, or accepting an unrelated recovery
  detour does **not** increment this.

## Game-test acceptance

Use a disposable Creative test world and a Survival-mode player target.
Place a Skeleton 8–12 blocks from the target and a Zombie between them,
with free blocks on either side. The Zombie may choose a nearby clear
side square rather than continuing along the blocked shot lane. Confirm
that the Skeleton can still fire and the Zombie ultimately retains its
normal melee attacks.

Repeat with both side squares blocked by Minecraft blocks: the Zombie
should keep its original route instead of claiming to have found a
valid alternative. Repeat while the Zombie is almost in melee range
(<4.5 blocks from the player): it should **not** receive an extra
bow-lane diversion. Also repeat with two Skeletons at different
angles: the new Zombie candidate must be clear for both visible
game shooting lines.

The pure JUnit suite validates local corridor geometry, deterministic
side choice, close-melee exclusion, visibility/confidence gates,
unavailable paths, and multi-Skeleton line clearance. Standard runtime
Minecraft GameTests cover the original native attacks and goal handoff.
A full manual movement test is still necessary on uneven terrain.
