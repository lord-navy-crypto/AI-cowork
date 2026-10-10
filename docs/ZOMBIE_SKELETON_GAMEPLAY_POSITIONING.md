# Minecraft-only Zombie and Skeleton movement upgrades

This iteration changes NPC game movement decisions, not attack damage,
arrows, explosions, weapons or the set of permitted Minecraft targets.

## Why this change is behavioral, not a UI mockup

**Skeletons:** Previously the ordinary vanilla bow goal could always take
movement control inside 15 blocks with a clear shot, even when the swarm
planner had proposed a tactical location. A Skeleton seeing a Survival-mode
player closer than 6 blocks now considers a short 3-block step **away**
from that directly observed player, up to a 7.5-block separation.

The proposed destination must be valid in already loaded Minecraft chunks,
have solid footing and body clearance. Only then does the actual MOVE Goal
temporarily retain control before the vanilla bow handoff. If the square is
blocked, the Skeleton remains able to shoot. At 6+ blocks the bow has its
previous firing behavior; at the reached position it also regains bow
control. This is conservative game spacing, not an aim-bot or new attack.

**Zombies:** A Zombie assigned FLANK_LEFT / FLANK_RIGHT can finish a
very short remaining sideways waypoint even after entering the normal
3.25-block melee handoff envelope, provided it has directly seen the
Minecraft player and is still more than 2.5 blocks away from that target.
At 2.5 blocks or closer, vanilla melee movement always takes precedence.
CHASER Zombies continue their prior immediate melee handoff, and
engineering/resource workers are not reassigned by this mechanic.

## Test in a disposable world

1. Spawn Zombies and Skeletons near a Survival-mode player in an open,
   flat game arena. Stand at different distances from a Skeleton and
   observe the moment it changes from bow firing to a short movement.
2. Put Minecraft obstacles behind a Skeleton. A blocked reposition
   attempt should not prevent its existing bow from functioning.
3. Build a small wall that breaks line of sight. Skeletons cannot use
   exact hidden-player positions for the new spacing step.
4. In a mixed group, observe flank-role Zombies approaching and then
   returning to vanilla melee instead of permanently pacing around.
5. Inspect the server's actual values:

```mcfunction
/swarmmobs inspect
/swarmmobs group
/swarmmobs panel
```

`skeletonSpacing` is current Skeleton movement participation and
`skeletonSpacingEpisodes` counts real off->on transitions. They are
not decorative UI counts.

## Known boundaries

A checked nearby square is not a guaranteed full path; Minecraft's
ordinary pathfinding remains authoritative. This iteration does not
guarantee Skeletons win combat or Zombies successfully traverse all
Minecraft blocks. Java unit tests check bounded geometry and attack
handoff thresholds; existing runtime GameTests and human playtests
cover integration, not perfect movement on all terrain.
