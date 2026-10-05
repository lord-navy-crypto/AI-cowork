# Swarm AI for Minecraft — development repository

This repository has been repurposed for a new **Minecraft multi-agent / swarm-intelligence project**.

The previous **AI CoWork** project has been preserved in two places:

- OpenPenguin integration branch: `lord-navy-crypto/Openguin@ai-cowork-integration`
- Archive branch in this repository: `archive/ai-cowork-2026-10-05`

## Project direction

The new project will explore coordinated agent behavior inside Minecraft, with ideas drawn from swarm robotics, distributed systems, multi-agent planning, and AI.

Planned directions include:
- local sensing and neighbor discovery;
- cohesion, separation, alignment, and obstacle avoidance;
- distributed communication between agents;
- role assignment and task allocation;
- shared or partially shared target memory;
- configurable communication delay, packet loss, sensing noise, and failures;
- swarm-state visualization and debugging;
- player-controlled or player-tamed swarms for exploration, transport, defense, and other tasks;
- later experimentation with learning-based policies once deterministic baselines are stable.

## Engineering principle

The goal is not only to make mobs "smarter." The project should expose the underlying mechanism:

```text
perception
   ↓
local communication
   ↓
coordination / task allocation
   ↓
decision
   ↓
movement / action
   ↓
feedback
```

The system should make these stages observable and tunable so the repository can also function as a small multi-agent experimentation platform.

## Status

**Bootstrap / architecture stage.**

The old AI CoWork implementation is no longer developed on `main`. Use the archive branch above if historical AI CoWork source is needed.
