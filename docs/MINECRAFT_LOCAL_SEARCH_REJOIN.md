# Minecraft Swarm Mobs — Decentralized Search Rejoining

This adds a bounded rejoin correction to **Minecraft game NPC movement**
when their ordinary target observation has become too uncertain for direct
engagement. It is not a global controller or a new target acquisition method.

## Problem and mechanism

Previous search already rotated deterministic local sectors around the
last-known target. The real waypoint was planned individually, so a loose
group could scatter even when multiple same-target allies remained nearby.

The new `SwarmSearchRallyPolicy` uses ONLY the existing locally collected
same-target teammate positions, without loading chunks or consulting a hidden
live target. During `SEARCH`, an agent may steer the current waypoint
slightly toward the teammate centroid when:

1. At least **two** locally seen nearby allies share the same known target.
2. Those allies are located within **12 blocks of the last observed target**
   (otherwise their positions are not treated as a reliable rejoin cue).
3. The current mob is at least **7 blocks** from the group centroid.
4. Division-of-labor coordination is enabled.

The waypoint correction is capped at **2.5 Minecraft blocks**. The original
sector rotation, separation, Minecraft navigation and terrain validation
remain in control. No modification of attack damage, explosions, player
tracking, health or game time.

A locally separated mob with no valid teammate sightings continues its
existing independent SEARCH path. When a new target becomes visible,
`searchRallyActive` switches off; changing or forgetting the target also
clears this temporary rejoin state.

## Test and inspect

In a disposable world, get a same-target group to chase a Survival-mode
player, then break line of sight behind blocks. Use:

```mcfunction
/swarmmobs inspect
/swarmmobs group
/swarmmobs panel
```

Read `searchRallyActive` and `searchRallyEpisodes` on the nearest
mob, or `regroupingSearchAgents` / `regroupingEpisodes` for the group.
The Command Center Overview shows the same live server-sourced counts.

Expected:
- Some separated same-target monsters search and modestly converge.
- Close groups keep different search sectors; they do NOT all rush the
  centroid.
- A monster alone, pursuing a different target, or lacking recent
  target-memory evidence does not fake a shared search waypoint.
- Reacquisition returns the NPC to existing ENGAGE behavior.

`searchRallyEpisodes` counts transitions from *not regrouping* to
*regrouping* (not every planner update).

## Evidence boundary

JUnit covers bounded corrections, teammate scarcity, invalid coordinates,
last-known-area limits, target changes and episode accounting.
Existing Runtime GameTests must pass before merging. No claim is made that
these adjustments prove reliable movement across every Minecraft biome,
obstacle type or loaded/unloaded-chunk boundary; that requires interactive
world testing.
