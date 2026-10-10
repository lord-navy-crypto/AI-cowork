# Minecraft SWARM-MOBS: four-species local battle coordination

This update operates only inside the Minecraft mod's NPC movement planner.
It does not change arrow damage, melee damage, Creeper fuse, target acquisition,
extra physics or spawn/colony rights. There is no centralized squad controller,
no synthetic combat turns, and no hidden player location access.

## Shared local movement principle

A same-target NPC group may include Zombies (assault), Skeletons (ranged
support), Spiders (flankers), and Creepers (breachers). Each agent already
samples nearby target-compatible teammates. A new
`SwarmFourSpeciesRoutePolicy` uses that snapshot to compare its existing
planned destination against a pair of short ±2.25-block side destinations.

Only a **directly observed**, confidence >=0.70 player, enabled division of
labor, and actual congestion can activate the new route choice. The new side
must reduce the number of relevant teammates within a 2-block circle, lie
on a solid, clear, already-loaded Minecraft square, and stay outside the
species-specific close-range combat protection radius. If neither side is
better, the old plan stays. Previously selected feasible side is favored
when occupancy differs by no more than one teammate.

Species distinctions:
- **Zombies:** add local same-role lane spacing on top of the existing
  front-anchor, missing-flank backfill and voluntary yield to Skeleton
  bow corridors. Active Skeleton bow-lane yield and colony
  ENGINEERING/MATERIAL tasks are never overwritten.
- **Skeletons:** avoid stacking same-species ranged support positions
  only when beyond normal nearby attack protection. Any new location must
  ALSO pass real game line-of-sight and same-target ally-shot clearance.
  Existing high-priority bow handoff and short close-range spacing win.
- **Spiders:** when two or more same-target Spiders are locally observed,
  assign left/right flanks in stable UUID order, independently of original
  formation-slot coincidence. Nearby same-species crowding can additionally
  propose a less occupied flank game waypoint; solo Spider preserves its
  existing role.
- **Creepers:** consider *all* same-target nearby allies when assessing a
  congested route, not merely other Creepers. Stop optional lane spacing
  before close-range fusion and whenever ignition/swell has started;
  original vanilla Creeper swelling retains priority.

Vertical difference between the player and self must be <=2.5 blocks
for a two-dimensional traffic decision; counted teammates must be within
2 blocks of the agent's height. This avoids inventing a shared corridor
between different Minecraft floors. Players behind walls or only heard
by message relays never activate new direct-sight spacing.

All routes continue to flow through the existing movement Goals,
PathNavigation acceptance checks, obstacle recovery, and vanilla combat
handoff. No claim of success is made for a planned point: server telemetry
records *actual accepted* direct PathNavigation.moveTo commands.

## Inspect actual game behavior

```mcfunction
/swarmmobs inspect
/swarmmobs group
/swarmmobs panel
```

The panel displays the number of currently proposed four-species spacing
plans, plus cumulative navigator-accepted game moves separately for
Zombies, Skeletons, Spiders and Creepers. Proposals and accepted paths
are distinct; accepted commands still do not guarantee destination arrival.

## Recommended disposable-world checks

1. Two Spiders and one player: verify opposite flank roles rather than
   both Spiders receiving the same slot-side assignment. Remove one
   Spider and inspect the remaining role. Repeat with three.
2. A cluster of Zombies: verify the new route is adopted only when a
   side has fewer same-type neighbors and collision-free footing.
   Confirm they still engage in vanilla melee when already close.
3. Skeleton group: verify visible shooting remains available and any
   alternative support square has clear line of sight and friendly corridor.
4. Multiple Creepers and Zombies: before fuse range, observe whether a
   congested candidate destination can be replaced by an open route.
   As soon as a Creeper ignites or enters its normal swell range, vanilla
   fuse handoff takes precedence.
5. Repeat all four in narrow Minecraft corridors, blocked side squares,
   different Y levels, and with no direct player observation. The system
   must preserve prior legal movement instead of inventing traversability.

Pure JUnit tests validate geometry, all four archetypes, bounded side
choice, original-route fallback, near-native-attack protection, deterministic
Spider flank distribution and membership-loss behavior. Existing runtime
GameTests are required for integration but cannot replace client testing
with real pathfinding and large mixed swarms.
