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
explicit local target messages
        ↓
range / latency / packet-drop model
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

## Target sharing and communication

A zombie learns about a player through direct line-of-sight perception or through explicit target messages sent by nearby swarm peers.

A target message carries:

```text
senderId
targetId
observationTick
sentTick
deliverTick
```

The communication model exposes:

```text
communication.radius
communication.latencyTicks
communication.packetDropRate
communication.experimentSeed
```

Messages are queued in a bounded per-agent inbox. A delayed message is not visible to target selection before `deliverTick`. Packet-loss decisions are deterministic for the same sender, receiver, message timing, target identity, and experiment seed.

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

Each agent builds a deterministic ordering of the UUIDs visible in its local movement-neighbor set, including itself:

```text
orderedMembers = sort(unique(self + localNeighborUUIDs))
slot = indexOf(self) mod formationSlots
```

If the same fully connected local group contains no more agents than available slots, members receive collision-free local slots without a central coordinator. If agents observe different neighbor sets, their local slot views can differ; this is intentional and becomes part of the distributed-consistency problem.

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
7. GameTest-server tests demonstrate runtime integration, visible-player perception, swarm-to-vanilla movement handoff, occluded target relay, and target-memory expiry.

## Known limitations

- Only vanilla zombies are swarm-enabled.
- Neighbor discovery currently queries nearby entities directly; a spatial hash will replace this for larger swarms.
- Swarm movement currently uses one high-priority MOVE goal; richer arbitration with flee/sun/terrain behaviors is still future work.
- The first communication fault model now supports range, latency, deterministic packet loss, bounded inboxes, and message telemetry; burst loss, bandwidth limits, and topology-aware routing are still future work.
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
3. **multi-agent simulation tests** — deterministic agents populate the role geometry and exercise local slot allocation;
4. **communication tests** — range, deterministic packet loss, delivery latency, inbox delay, and deduplication;
5. **Minecraft GameTests** — runtime attachment/goal injection, target acquisition, melee handoff, occluded relay, and memory expiry.
