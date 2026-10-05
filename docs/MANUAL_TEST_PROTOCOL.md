# Swarm Combat v0.1 — Manual Runtime Test Protocol

This protocol complements JUnit by checking whether the swarm behaves as expected inside a real Minecraft world.

## Test environment

Recommended:

- Minecraft 1.21.1
- NeoForge 21.1.249
- Java 21
- Swarm Mobs branch: swarm-combat-v0.1
- Survival or Adventure mode for the target player

Useful commands:

~~~
/swarmmobs status
/swarmmobs inspect
/swarmmobs group
/swarmmobs debug spawn <2..32>
/swarmmobs debug particles on
~~~

## Test A — Open-field swarm acquisition

1. Stand in an open, flat area in Survival or Adventure mode.
2. Run /swarmmobs debug spawn 8.
3. Allow several planning updates.
4. Run /swarmmobs group.

Expected evidence:

- agents reports the nearby zombies.
- targetKnown rises toward the group size.
- direct is high in a fully visible open field.
- multiple role classes are normally present.
- avgSeparation becomes nonzero while agents are close enough to repel one another.
- agents receive role-dependent destinations instead of one identical path point.

Use /swarmmobs inspect to read the nearest agent's role, stable slot, local neighbor count, target age, direct-vs-relayed source, planned destination, separation magnitude, and cohesion magnitude.

## Test B — Occlusion and memory expiry

Purpose: verify that target memory is based on real perception rather than an endlessly refreshed vanilla target.

1. Spawn a test swarm while the player is visible.
2. Move behind an opaque wall or around terrain so the swarm loses line of sight.
3. Repeatedly run /swarmmobs inspect.

Expected behavior:

While no new direct observation exists, targetAgeTicks should increase.

The original observation timestamp must not become younger merely because another mob relays it.

With the default targetMemoryTicks = 100, stale player memory should eventually expire if no swarm member obtains a fresh line-of-sight observation.

After expiry, the swarm layer clears its stale player target and yields back to normal behavior until the player is observed again.

## Test C — Relay through local neighbors

Create terrain where:

- one or more zombies have clear line of sight to the player;
- some nearby zombies are occluded;
- the occluded zombies remain within neighborRadius of a directly observing swarm member.

Expected evidence for an occluded receiving agent:

~~~
targetKnown = true
directObservation = false
~~~

Its target observation tick should match the source observation rather than the relay time.

## Test D — Role geometry

Use roughly 8–12 agents in open terrain.

Expected qualitative geometry:

~~~
                CHASER
                  ↓
       LEFT   [player]   RIGHT

                 REAR
                  ↑
~~~

- CHASER approaches near the forward pressure lane.
- FLANK_LEFT receives a destination on the player's local left.
- FLANK_RIGHT receives a destination on the player's local right.
- REAR_PRESSURE receives a destination behind the player's facing direction.
- lane offsets separate multiple agents with the same role.

The exact final paths still depend on vanilla PathNavigation and terrain.

## Test E — Local anti-crowding

Spawn a dense group and inspect several times during approach.

Interpretation:

- separation > 0 means nearby peers are producing a repulsive correction.
- cohesion > 0 means the local neighbor centroid is producing an attraction direction.

Current destination:

~~~
destination =
    roleDestination
  + separationVector × separationWeight
  + cohesionVector × cohesionWeight
~~~

## Test F — Small scaling check

Repeat with:

~~~
/swarmmobs debug spawn 8
/swarmmobs debug spawn 16
/swarmmobs debug spawn 32
~~~

Observe server responsiveness and compare avgNeighbors.

v0.1 caps each agent's planning input with maxNeighbors, but neighbor discovery still performs local entity queries. A later milestone can replace this with a spatial index for larger swarms.

## Pass criteria for the current alpha

The runtime milestone is considered healthy when:

1. visible zombies learn the same player target;
2. occluded neighbors can receive recent target memory;
3. relaying does not refresh the source observation timestamp;
4. stale target memory can expire;
5. role destinations are geometrically meaningful;
6. close agents produce nonzero separation;
7. the swarm goal controls long-range approach but yields near the player;
8. the server remains responsive at the small test sizes above.

This protocol provides runtime evidence and complements future automated GameTests.


## Visual debugger

Enable:

~~~
/swarmmobs debug particles on
~~~

Current visual meanings:

- END_ROD line: agent to its current planned destination;
- HAPPY_VILLAGER marker: the current destination point;
- ELECTRIC_SPARK line: a sampled local neighbor relationship;
- CRIT marker: current player target.

The visualizer is intentionally server-side and uses only vanilla particles. It is a development aid, not the final client HUD.

Disable with:

~~~
/swarmmobs debug particles off
~~~
