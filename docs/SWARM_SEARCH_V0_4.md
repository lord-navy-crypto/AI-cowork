# Swarm Search v0.4 — Non-AI Target Reacquisition

This branch adds a decentralized search layer on top of the stable heterogeneous swarm.

## Why

Earlier versions preserved a truthful last-known target snapshot and confidence decay,
but stale agents still used their normal combat formation around that old point.

v0.4 introduces an explicit behavior-mode transition:

~~~text
direct observation
      ↓
   ENGAGE

indirect observation + sufficient confidence
      ↓
   ENGAGE

indirect observation + low confidence
      ↓
   SEARCH

memory expires
      ↓
target forgotten
~~~

No live player position is read through walls.

## Search geometry

As confidence falls, uncertainty rises:

~~~text
uncertainty = 1 - confidence
searchRadius = lerp(searchMinRadius, searchMaxRadius, uncertainty)
~~~

Capability bands then specialize coverage:

- ASSAULT searches the inner area;
- FLANKER searches the middle/wider area;
- RANGED_SUPPORT covers the outer perimeter.

Agents with the same capability use stable local slots to occupy different sectors.

Search sectors rotate in discrete phases as target age increases so the swarm does not
freeze on one static ring.

## Existing local steering remains active

SEARCH destinations still receive:

- separation;
- cohesion;
- alignment;
- steering hard cap.

This preserves local collision avoidance and swarm coherence while exploring.

## Configuration

~~~text
searchConfidenceThreshold = 0.45
searchMinRadius = 2.0
searchMaxRadius = 10.0
searchPhaseTicks = 20
searchArrivalTolerance = 1.25
~~~

## Telemetry

/swarmmobs inspect adds:

- mode;
- searchRadius.

/swarmmobs group adds:

- engage count;
- search count;
- average search radius.

## Runtime verification

A Minecraft Runtime GameTest:

1. lets a Spider directly observe a player;
2. blocks line of sight;
3. moves the player behind the wall;
4. waits until confidence falls below the search threshold;
5. verifies the Spider:
   - keeps the old target snapshot;
   - switches to SEARCH;
   - has a positive search radius;
   - receives a destination offset from the stale target center.

The design stays deterministic and does not use Ollama or any external AI.


## v0.4.1 — bounded target-motion prediction

SEARCH can now shift its search anchor using the velocity captured in the last real
target observation.

The prediction is deliberately conservative:

~~~text
observed velocity at sighting
        ↓
bounded prediction horizon
        ↓
confidence-weighted displacement
        ↓
maximum prediction distance clamp
        ↓
predicted SEARCH anchor
~~~

Defaults:

~~~text
searchPredictionMaxTicks = 30
searchPredictionMaxDistance = 6.0
~~~

Prediction trust falls with target confidence. As confidence approaches zero, the
velocity-based offset also approaches zero while the decentralized search radius
continues to expand.

The system never reads a hidden player's current position or current velocity.

Target velocity is part of the immutable TargetObservation and is preserved through
neighbor-to-neighbor communication exactly like the original position and timestamp.

/swarmmobs inspect exposes:

- observedVelocity;
- predictedSearchAnchor;
- predictionOffset.
