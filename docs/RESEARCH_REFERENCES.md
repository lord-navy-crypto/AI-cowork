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

## Communication robustness and information sharing

The communication fault layer is motivated by multi-robot research that evaluates coordination under changing network conditions rather than assuming perfect instantaneous communication.

### Jo & Kwon (2025) — decentralized asynchronous information sharing

Daeil Jo and Yongjin Kwon, "Generation of Critical Information and Sharing Mechanism for Multi-Robot Mission Success," IEEE Access, 2025.

The work evaluates decentralized asynchronous multi-robot information sharing under different network conditions and explicitly measures message delay, reaction time, packet loss, and mission success.

Swarm Mobs mirrors this experimental framing by exposing communication delay and packet loss separately from movement behavior.

### Meriaux & Weitzen (2024) — swarm robustness to packet loss

Edwin Meriaux and Jay Weitzen, "Robustness of Couzin Swarming to Packet Loss and Methods to Improve Robotic Swarm Communication," IEEE COMCAS, 2024.

The paper parametrically studies swarm behavior under packet communication loss, including randomly occurring and burst loss.

Swarm Mobs v0.1 starts with deterministic per-message loss. Burst-loss models are intentionally reserved for a later milestone.

### Chen et al. (2020) — dynamic and partitioned swarm networks

Wu Chen, Jiajia Liu, Hongzhi Guo, and N. Kato, "Toward Robust and Intelligent Drone Swarm: Challenges and Future Directions," IEEE Network, 2020.

The paper highlights dynamic topology, intermittent links, capability constraints, and network partitioning as important swarm-network problems.

This motivates treating communication radius as distinct from movement-neighbor sensing and later studying network fragmentation.

## Current communication abstraction

~~~
sender target memory
        ↓
communication-range check
        ↓
deterministic packet-drop decision
        ↓
message with sentTick / deliverTick
        ↓
bounded receiver inbox
        ↓
delivery after configured latency
        ↓
freshness policy
        ↓
receiver target memory
~~~

This is still a simplified model. It does not yet model bandwidth contention, burst loss, retransmission, wireless interference, or multi-channel routing.
