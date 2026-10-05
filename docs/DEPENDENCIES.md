# Dependency Policy

Swarm Mobs v0.1 intentionally keeps the runtime dependency surface small.

## Required runtime dependency

### NeoForge

**Required.**

Current development target:

- Minecraft: `1.21.1`
- NeoForge: `21.1.249`
- Java: `21`

NeoForge provides everything required for the first swarm milestone:

- mod lifecycle and event bus;
- `EntityTickEvent.Post`;
- Data Attachments for adding swarm state to vanilla entities;
- server configuration;
- networking for later debug visualization;
- GameTest support.

## Development-only dependency

### JUnit 5

**Development/test only. Not required by players.**

The mathematical planner is deliberately separated from Minecraft-specific event code so formation and steering rules can be tested without launching the game.

## Optional future integrations

These are useful, but they are **not required in v0.1**.

### Jade — recommended later, optional

Best future use:
- inspect agent role;
- neighbor count;
- shared-target age;
- communication state;
- local steering terms.

Why not required now:
- core swarm logic must remain server-side and usable without a HUD mod.

### Cloth Config — optional

Could provide a polished client configuration screen later.

Why not required now:
- NeoForge's native `ModConfigSpec` already handles the current tuning parameters.

### spark — development tool, not a mod dependency

Useful later for profiling:
- server tick cost;
- entity AI hotspots;
- scaling from 20 to 50 to 100 agents.

It should be treated as an external profiling tool, not linked into Swarm Mobs.

## Dependencies intentionally avoided

### GeckoLib

Not needed in the first milestones because Swarm Mobs modifies vanilla mobs and does not introduce custom animated entities.

### JEI

No recipe/progression system exists yet.

### Fusion

No custom connected-texture system exists yet.

### Smart AI / behavior libraries

Not used in the swarm core.

The purpose of this project is partly to expose and study our own local coordination algorithm. Hiding the central behavior behind a large AI framework would make the experimental layer harder to inspect and compare.

## Rule for adding a hard dependency

A dependency becomes required only if all three are true:

1. it removes substantial duplicated infrastructure;
2. it does not hide a core swarm algorithm we want to study;
3. the benefit is important for normal players, not only developers.

For the current milestone, **NeoForge is the only runtime dependency we actually need**.
