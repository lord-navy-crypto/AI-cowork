# Swarm Prediction v0.5 — Bounded Observed-Motion Lead

This branch adds conservative short-horizon target prediction to ENGAGE mode.

## Fairness boundary

Prediction uses only data captured during a truthful target observation:

- observed position;
- observed facing;
- observed horizontal velocity;
- observation timestamp.

The velocity snapshot is relayed unchanged through the existing communication layer.

The planner never queries a hidden player's live position or velocity.

## Prediction model

A short requested horizon combines observation age and a small lead:

~~~text
requestedTicks = min(maxPredictionTicks, observationAge + leadTicks)
effectiveTicks = requestedTicks * confidence
predictedOffset = observedVelocity * effectiveTicks
~~~

The final offset is hard-capped by targetPredictionMaxDistance.

Defaults:

~~~text
targetPredictionEnabled = true
targetPredictionLeadTicks = 6
targetPredictionMaxTicks = 12
targetPredictionMaxDistance = 3.5
~~~

## Behavior boundary

ENGAGE:
- prediction may shift the tactical target point forward.

SEARCH:
- prediction is cleared;
- search remains centered on the truthful last-known observation snapshot.

This prevents stale velocity from dragging a search pattern indefinitely away from the
last observed location.

## Telemetry

/swarmmobs inspect adds:

- predictedTarget;
- predictionOffset.

/swarmmobs group adds:

- predictionActive;
- average prediction offset.

## Verification

Unit tests verify:

- fresh moving targets receive a short lead;
- lower confidence reduces prediction;
- maximum offset is hard-capped;
- stationary targets are not given synthetic motion;
- relayed messages preserve the exact observed velocity.

All existing Minecraft Runtime GameTests remain enabled to guard the perception,
communication, heterogeneous-role, search-mode, and memory-expiry behavior.
