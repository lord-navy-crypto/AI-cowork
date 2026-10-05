# Swarm Combat v0.1 — Development Plan

## Scope

The first implementation deliberately focuses on the easiest behavior to observe and test:

> a local group of vanilla zombies cooperates against the same player target.

No external AI/LLM controls movement in v0.1. The `externalAi.enabled` configuration key is reserved only for later experiments.

## Control loop

```text
local player observation
        ↓
target memory
        ↓
neighbor target sharing
        ↓
stable role / formation slot
        ↓
formation destination
        ↓
separation + cohesion correction
        ↓
SwarmApproachGoal owns MOVE while outside release radius
        ↓
vanilla PathNavigation
        ↓
inside release radius → vanilla close-range combat behavior
```

The swarm layer does not replace Minecraft's final attack behavior. A dedicated `SwarmApproachGoal` temporarily owns the MOVE channel while an agent is outside `releaseToVanillaDistance`; once inside that radius, the goal yields and vanilla melee behavior takes over.

## Local information model

Each zombie has transient `SwarmAgentState` attached through NeoForge Data Attachments.

The state currently contains:

- shared target UUID;
- original observation tick;
- whether the target was directly observed or relayed;
- local neighbor count;
- deterministic formation slot;
- current role;
- current planned destination;
- next planning tick.

The attachment is intentionally transient in v0.1. Restarting the world resets swarm memory.

## Target sharing

A zombie can learn about a target in three ways:

1. direct line-of-sight observation;
2. vanilla target acquisition;
3. recent memory relayed by a nearby swarm peer.

Relaying preserves the original observation timestamp. A relay therefore cannot make stale information look new.

If

```text
age = currentTick - observationTick
```

then the information is accepted only while

```text
age <= targetMemoryTicks
```

## Formation slots

Every mob receives a deterministic slot derived from its UUID:

```text
slot = floorMod(hash(UUID), formationSlots)
```

This makes assignment stable without a central coordinator.

Roles repeat every four slots:

```text
0 → CHASER
1 → FLANK_LEFT
2 → FLANK_RIGHT
3 → REAR_PRESSURE
```

CHASER units move directly toward the target.

Roles now map to semantic target-relative geometry instead of a generic ring:

```text
CHASER
    target + right × laneOffset

FLANK_LEFT
    target - right × formationRadius
           + forward × laneOffset

FLANK_RIGHT
    target + right × formationRadius
           + forward × laneOffset

REAR_PRESSURE
    target - forward × formationRadius
           + right × laneOffset
```

`laneOffset` is derived from the stable formation slot, so two agents with the same role receive distinct nearby lanes rather than collapsing onto exactly the same destination.

## Separation

Nearby peers create a repulsive steering vector.

For each peer inside `separationRadius`:

```text
strength = (radius - distance) / radius
repulsion = normalize(self - peer) × strength
```

All repulsion terms are summed.

This is intended to reduce mob crowding and improve access to different sides of the target.

## Cohesion

The local neighbor centroid is

```text
centroid = mean(neighbor positions)
```

The cohesion direction is

```text
normalize(centroid - self)
```

A small cohesion weight discourages isolated swarm members from drifting too far from the local group.

## Final movement destination

```text
destination =
    formationDestination
  + separation × separationWeight
  + cohesion × cohesionWeight
```

Once a mob is within `releaseToVanillaDistance`, the swarm layer stops issuing formation movement so vanilla close-range AI can dominate.

## v0.1 success criteria

The first milestone is considered successful when:

1. one zombie seeing a player can propagate that player target to nearby swarm zombies;
2. multiple zombies do not all receive the exact same approach position;
3. close neighbors create measurable separation steering;
4. role and formation assignment remain stable for a given UUID;
5. planner unit tests pass;
6. the NeoForge project compiles and packages successfully;
7. a later GameTest can demonstrate the same behavior inside a real test world.

## Known limitations

- Only vanilla zombies are swarm-enabled.
- Neighbor discovery currently queries nearby entities directly; a spatial hash will replace this for larger swarms.
- Swarm movement currently uses one high-priority MOVE goal; richer arbitration with flee/sun/terrain behaviors is still future work.
- There is no packet-loss, latency, or communication graph yet.
- There is no client debug overlay yet.
- There is no learned policy, RL, MARL, or LLM controller.

These are intentional limits for the first testable version.


## Debug commands

The development branch exposes three commands:

```text
/swarmmobs status
/swarmmobs inspect
/swarmmobs debug spawn <2..32>
```

For a real targeting test, run the spawn command while the player is in Survival or Adventure mode. The command creates a ring of vanilla zombies around the player. The event layer then assigns target memory, formation slots, roles, and planned destinations.

`/swarmmobs inspect` reports the nearest zombie's role, slot, neighbor count, target UUID, target age, and whether the latest target source was a direct observation.

## Automated verification

The v0.1 branch now separates testing into three layers:

1. **planner unit tests** — vector behavior, slot stability, role mapping;
2. **target relay tests** — freshness, expiry, timestamp preservation;
3. **multi-agent simulation tests** — eight deterministic agents populate all four roles and produce a distributed formation.

A real GameTest-world suite remains planned after the deterministic baseline stabilizes.
