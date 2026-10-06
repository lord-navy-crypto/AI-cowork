# Ollama AI Shadow Mode — v0.10

## Purpose

AI Shadow Mode adds a **slow, high-level local strategy advisor** above the deterministic swarm stack.

It does not control:
- per-tick movement;
- attacks;
- exact coordinates;
- path nodes;
- collision or walkability;
- individual mob actions;
- live swarm configuration.

The deterministic controller remains the only gameplay controller.

## Architecture

```text
v0.9 aggregate telemetry
        ↓
SwarmStrategyRequest
        ↓
local Ollama /api/chat
        ↓
JSON-schema structured decision
        ↓
sanitize + clamp
        ↓
SwarmAiShadowState
        ↓
commands / control panel only

NO gameplay write path
```

## Local-only boundary

Default endpoint:

```text
http://127.0.0.1:11434
```

The client accepts loopback hosts only:
- localhost
- 127.0.0.1
- ::1

Remote hosts and LAN addresses are rejected in v0.10.

## Input telemetry

The model receives aggregate state only, including:
- agent count;
- experiment preset and elapsed ticks;
- observed/configured communication drop rate;
- communication latency;
- sensing dropout/noise;
- detours and recoveries;
- recovery failure rate;
- PathNavigation query count;
- role reassignment count;
- active SEARCH episodes;
- SEARCH success rate;
- average reacquisition latency.

No per-agent exact world coordinates are included.

## Structured output

Allowed strategy modes:

```text
BASELINE
ENCIRCLE
CONCENTRATE
REGROUP
SEARCH
```

Bounded multipliers:

```text
formationRadiusMultiplier  0.70 .. 1.50
separationMultiplier       0.70 .. 1.50
cohesionMultiplier         0.70 .. 1.50
searchRadiusMultiplier     0.80 .. 1.40
```

Returned values are sanitized again in Java even though the request supplies a JSON Schema.

## Threading

Ollama HTTP uses Java 21 `HttpClient.sendAsync`.

The Minecraft server thread does not wait synchronously for inference. Completion is scheduled back onto the server executor before shadow state is updated.

Only one shadow request may be in flight at a time.

## Failure behavior

If AI is disabled, unavailable, times out, or returns invalid output:
- deterministic gameplay continues unchanged;
- the shadow request records fallback/error telemetry;
- no swarm parameter is modified.

## Commands

```text
/swarmmobs ai status
/swarmmobs ai models
/swarmmobs ai model <name>
/swarmmobs ai on
/swarmmobs ai off
/swarmmobs ai shadow
```

The control panel has an **AI Shadow** page for enable/disable, one-shot inference, refresh, and recommendation telemetry.

## Why Shadow first

The project now has a strong deterministic baseline and measurable outcomes. AI should first be evaluated as an advisor before it is allowed to influence high-level parameters.

A later active mode, if added, should require:
- measured improvement over deterministic baselines;
- bounded parameter ranges;
- strategy TTL;
- anti-thrashing hysteresis;
- deterministic fallback;
- explicit user opt-in.


## Optional bounded active strategy

Shadow behavior remains the default. Active gameplay influence is a separate explicit opt-in:

```text
/swarmmobs ai active on
/swarmmobs ai active apply
/swarmmobs ai active status
/swarmmobs ai active off
```

An active request still uses the same aggregate telemetry and sanitized strategy schema.

Allowed gameplay influence is restricted to:
- formation-radius multiplier;
- separation multiplier;
- cohesion multiplier;
- search-radius multiplier;
- a narrow ASSAULT-only role bias for strategy modes such as ENCIRCLE or CONCENTRATE.

Specialist capabilities remain authoritative:
- Skeleton stays RANGED_SUPPORT;
- Spider stays FLANKER;
- Creeper stays BREACHER.

The active overlay has:
- a configurable TTL;
- minimum mode-hold hysteresis;
- deterministic fallback after expiry;
- immediate clear when active mode or the AI provider is disabled.

It still cannot provide exact coordinates, choose path nodes, issue attacks, bypass collision/walkability, or replace species-specific vanilla combat handoff.
