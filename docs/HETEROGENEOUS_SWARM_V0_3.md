# Heterogeneous Swarm v0.3

This branch extends the deterministic non-AI swarm from one species to a capability-aware mixed team.

## First supported species

### Zombie — ASSAULT
- participates in CHASER / FLANK_LEFT / FLANK_RIGHT / REAR_PRESSURE roles;
- closes distance and yields to vanilla melee behavior near the target.

### Skeleton — RANGED_SUPPORT
- shares the same perception, target-memory, communication, and neighbor graph as Zombies;
- receives relayed target observations from nearby teammates;
- uses a larger standoff formation radius;
- repositions with the swarm movement goal;
- yields movement when near its support destination so vanilla bow combat can take over.

## Shared cooperation layer

Zombie and Skeleton agents now share:

- local target observations;
- last-known target snapshots;
- target confidence and memory expiry;
- communication range / latency / packet-loss model;
- neighbor separation, cohesion, and alignment;
- formation-slot hysteresis;
- group telemetry.

The key architecture is:

~~~text
shared perception + communication
            ↓
capability profile
            ↓
tactical role
            ↓
species-appropriate destination
            ↓
vanilla combat behavior
~~~

## Testing

Use:

~~~text
/swarmmobs debug spawnmixed 8
/swarmmobs debug particles on
/swarmmobs group
/swarmmobs inspect
~~~

The mixed spawn command alternates Zombie assault agents and Skeleton ranged-support agents.

A runtime GameTest verifies that a Zombie with direct line of sight can relay its player observation to an occluded Skeleton, and that the Skeleton enters RANGED_SUPPORT instead of being treated as a melee chaser.

## Current scope

v0.3 starts with Zombie + Skeleton only. Spider and other archetypes should be added only after this mixed-team baseline remains stable in runtime tests.
