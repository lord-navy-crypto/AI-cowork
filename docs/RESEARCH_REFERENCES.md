# Research and API References

This document records the sources used to shape the initial Swarm Mobs architecture.

## NeoForge

### Data Attachments

NeoForge Data Attachments can add custom state to entities that are not owned by the mod, which is why Swarm Mobs can attach agent memory to vanilla zombies instead of replacing them with custom entities.

Official documentation:
https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/

### Events

Swarm Mobs uses NeoForge's game event bus to keep the Minecraft integration layer separate from the pure planning algorithm.

Official documentation:
https://docs.neoforged.net/docs/1.21.1/concepts/events/

### Configuration

The initial tuning surface uses NeoForge `ModConfigSpec`.

Official documentation:
https://docs.neoforged.net/docs/1.21.1/misc/config/

### GameTest

GameTest will be used for reproducible in-world swarm experiments after the first compile/test milestone.

Official documentation:
https://docs.neoforged.net/docs/1.21.1/misc/gametest/

## Swarm behavior foundations

### Reynolds flocking / Boids

Craig Reynolds' classic flocking model demonstrates how local rules such as separation, alignment, and cohesion can create collective behavior without a central controller.

Reference:
https://www.red3d.com/cwr/boids/

Swarm Mobs v0.1 currently implements separation and cohesion in its approach planner. Alignment is reserved for a later movement-control pass.

## Multi-robot interpretation

The project treats Minecraft mobs as local agents:

```text
sensing → memory → neighbor information → local decision → navigation
```

The current version is intentionally a deterministic baseline. Later communication faults, decentralized task allocation, and learned policies should be compared against this baseline rather than replacing it before measurable behavior exists.
