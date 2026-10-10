# Adaptive Minecraft Swarm Tactics — playtest specification

These tactics apply ONLY to NPC monsters in **Minecraft**. They do not
implement real-world military actions or add any real-world targeting.
The behavior is purely local, bounded and restricted to game entities.

## What actually changes

Prior to this branch, combat formations use a fixed reference from the
observed player's facing direction. That made Spiders appear to stand beside
the player and Zombies converge as a single group.

The new `SwarmAdaptiveTacticsPolicy` chooses a game-specific spatial pattern:

- **SWEEP**: at least three members locally share a *recent* target observation,
  and observed Minecraft-player movement is significant but plausible.
  The formation axis aligns to the observed target-motion direction.
  Spider FLANK_LEFT / FLANK_RIGHT waypoints can move up to 2 blocks ahead
  of that trajectory. Zombie flanking waypoints move only half as far.
  This creates different movement paths from a direct frontal chase.
- **SURROUND**: same-target group is at least three, target is nearly still,
  and observation is recent. The planned formation axis follows the measured
  vector from the same-target group's centroid toward the target. Assigned
  roles occupy different local geometrical positions. One assault Zombie
  assigned REAR_PRESSURE can try a bounded route to the *other side* of
  the stationary observed target (at most 3 blocks beyond the target).
  It is a genuine navigational waypoint, not an instant teleport or a
  special attack. Spiders occupy the sides and the Creeper/Skeleton
  vanilla attack handoffs remain unchanged.
- **STANDARD**: solo/pair, stale target information, disabled division of
  labor, low confidence or unrealistic velocity. Retain the prior formation
  behavior and regular vanilla movement/attack handoffs.
- **SEARCH**: relayed, low-confidence observations continue through the
  existing bounded search controller, rather than using stale precise
  pursuit geometry.

This is **not** an artificial round-based mechanic: on each scheduled local
planning event, patterns follow the newest allowed local observations. No
global controller, teleportation, new attacks, faster fuse or free resources.

Existing Skeleton ranged corridors and the vanilla bow handoff remain.
There is one important cross-species interaction fix: when a **same-target**
Zombie/Creeper occupies the Skeleton's *current* two-dimensional firing
corridor, the Skeleton keeps its swarm MOVE goal instead of yielding movement
to the bow. Once the teammate clears that corridor, the ordinary vanilla bow
goal may run. This uses the already sampled local teammate positions; there
is no extra every-tick entity search in the bow Goal and it does not change
projectile damage or aiming.

Creeper explosion timing and Zombie safe-yield Goals remain unchanged.
Observed movement is bounded; Skeletons and Creepers do NOT gain the new
waypoint lead offsets.

## Practical experiment

Start a **new disposable test world** using the development build. Ensure
`Swarm master` and `Division of labor` are enabled in the Command Center.
For combat observations the player must be in Survival/Adventure (Creative
and Spectator players are intentionally not attack targets).

Run commands with operator permission:

```mcfunction
/swarmmobs debug spawn 12
/swarmmobs inspect
/swarmmobs group
```

1. First, stand still and verify the nearest agent reports
   `tacticalPattern=SURROUND` after local target observations have reached
   a group of at least three. Zombies and Spiders should use nonidentical
   planned waypoints.
2. Move laterally for a sustained few seconds; an agent that observes
   recent movement may report `tacticalPattern=SWEEP`. Check its
   `role`, `destination` and tactical pattern through `inspect`.
3. Hide behind blocks to interrupt direct observation: when target
   confidence is too low, it should fall back to `STANDARD` or
   `SEARCH`, not track the player perfectly through walls.
4. Spawn one Skeleton and Creeper with Zombies and Spiders.
   Confirm vanilla bow shooting and fuse still work and aren't blocked by
   the movement-policy change.
5. Repeat with 2, 4, 12 and 24 monsters; measure TPS/MSPT separately from
   path geometry, rather than inferring performance from visual impressions.

Only local same-target peers contribute to the tactic. The ordinary
neighbor separation/controller still considers nearby different-target mobs
for collision avoidance.

## Automated evidence and limits

Pure-policy JUnit cases cover stationary target geometry, directional
movement, ability-specific lead offsets, bounded displacement, no phantom
envelopment for solos/pairs, stale observation rejection and invalid values.

Unit tests and GameTests alone do NOT establish that the formation is
visually convincing on arbitrary Minecraft terrain or that it wins fights.
Manual tests should assess actual navigation progress, line-of-sight
handoffs and multi-agent state transitions rather than purely the mode names.
