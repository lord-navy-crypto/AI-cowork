# Swarm Core v0.2 — Non-AI Algorithm Upgrade

This branch deliberately keeps external AI out of the control loop.

The goal is to strengthen the deterministic/decentralized swarm baseline before any
Ollama strategy is allowed to influence gameplay.

## First upgrade batch

### 1. Last-known target snapshots

Target memory and communication now carry an immutable observation snapshot:

- target UUID;
- observation tick;
- observed X/Y/Z;
- observed facing direction.

An occluded swarm member plans toward the last observed position instead of resolving
the UUID back to the player's current live position.

This makes occlusion, communication latency, packet loss, and memory age materially
meaningful.

### 2. Target confidence

Confidence decays linearly across the configured target-memory window:

~~~
confidence = 1 - age / targetMemoryTicks
~~~

When an indirect target becomes stale, SwarmApproachGoal reduces movement speed toward
the configured staleTargetMinSpeedFactor instead of pursuing old information at full
confidence.

### 3. Alignment steering

The planner now includes a third local flocking term:

~~~
steering =
    separation * separationWeight
  + cohesion   * cohesionWeight
  + alignment  * alignmentWeight
~~~

Alignment steers an agent toward the average movement direction of moving local peers.

### 4. Bounded steering

The combined local steering correction is capped by maxSteeringCorrection before it is
added to the role destination. This prevents dense groups from producing arbitrarily
large correction vectors.

### 5. New telemetry

/swarmmobs inspect now exposes:

- targetConfidence;
- last-known target estimate;
- alignment magnitude;
- final steering magnitude.

/swarmmobs group exposes corresponding group averages.

## Research rationale

The design remains decentralized and local. Recent swarm-robotics literature continues
to emphasize local interaction, sparse/decentralized communication, and adaptation to
stale or constrained information. The core branch intentionally improves those
properties before adding any higher-level AI provider.

## Branch policy

- test-build/v0.1.0-alpha.1 remains frozen.
- ollama-interface-v0.2 remains a separate AI-interface experiment.
- swarm-core-v0.2 is the non-AI algorithm-development branch.
