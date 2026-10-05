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
vanilla PathNavigation
        ↓
vanilla close-range combat behavior
```

The swarm layer does not replace Minecraft's final attack behavior. It modifies target sharing and approach geometry.

## Local information model

Each zombie has transient `SwarmAgentState` attached through NeoForge Data Attachments.

The state currently contains:

- shared target UUID;
- original observation tick;
- whether the target was directly observed or relayed;
- local neighbor count;
- deterministic formation slot;
- current role;
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

Other roles use a point on a ring around the target:

```text
angle = 2π × slot / formationSlots

offset =
    right × cos(angle) × formationRadius
  + forward × sin(angle) × formationRadius

baseDestination = targetPosition + offset
```

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
- Vanilla zombie goals can still influence navigation between swarm planning updates.
- There is no packet-loss, latency, or communication graph yet.
- There is no client debug overlay yet.
- There is no learned policy, RL, MARL, or LLM controller.

These are intentional limits for the first testable version.
