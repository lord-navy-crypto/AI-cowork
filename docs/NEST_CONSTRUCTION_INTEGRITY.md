# Nest Shell Construction and Recovery

Minecraft 1.21.1 / NeoForge / Java 21 — this module uses a **small local shell**,
not a fully excavated ant hill or a guaranteed tunnel network.

## What changes

- A completed chamber remains a paid-for abstract capacity (+4 members).
  Visual shells are limited to two real world blocks per chamber module.
- When `nestVisibleExpansionEnabled` and `mobGriefing` are ON, the Nest Core
  checks previously constructed shell positions once per colony cycle
  (about every 200 ticks in loaded/observed chunks). It now repairs **one**
  missing soil or timber block per cycle, provided the original position is
  AIR, the correct supporting block exists, no entity collides and a player
  is farther than 12 blocks from the core.
- **No free repairs:** one soil shell replacement consumes **1 soil point**;
  one oak-log shell replacement consumes **3 timber points**. Stored resource
  totals drop by precisely the same amount only after world placement succeeds.
  If the required material is unavailable, the shell waits until workers
  deliver real drops to the core.
- **No overwrite:** occupied sites and fluid-obstructed sites are never
  replaced, including player-built blocks. Higher tiers are allowed only when
  resting on the nest's own registered lower-tier shell positions, rather
  than on unrelated dirt/log structures.
- In each cycle one successful repair takes precedence over creating a new
  chamber. This prevents limitless expansion while the paid-for structure is
  damaged. Existing `chamberLevel` and `visibleChamberLevel` remain stable
  through repairs. A new persistent `RepairedShellPieces` telemetry counter
  records successful paid replacements.

## Inspect actual work in-game

In a disposable experiment world with operator commands, enable colony work if
older world settings override new-world testing defaults:

```mcfunction
/swarmmobs debug testmode on
/give @s swarmmobs:nest_core
```

Place the core, face/aim directly at it (within 48 blocks), then type:

```mcfunction
/swarmmobs debug neststatus
/swarmmobs debug workstatus
```

`neststatus` reports independent soil, timber and food points, current
capacity, paid chambers, visible modules, repair counter, births and
deliveries. `workstatus` explains why the nearest Zombie is or is not
working. Observe the Nest Core from beyond the 12-block no-build zone,
but within its 48-block loaded-player activity range.

To verify repair, first complete a paid visual module (8 soil + 6 timber
points, at least three colony members near the core, enough population demand).
Remove just one shell dirt block in the disposable world, and provide at
least one new **real dropped dirt item** to the core. After a colony cycle,
confirm the same position is restored, `soilPoints` falls by 1 and
`paidRepairs` increases by one. Repeat with the raw log (three timber points).

## Limits

This does NOT make Zombies place blocks with a hand animation; the existing
server-authoritative Nest Core is the construction actor. Repair is deliberately
bounded, in-world, conservative and matter-accounted. Full excavated,
multi-room underground hives and autonomous mining architecture remain
outside this iteration.

The Runtime GameTest verifies conservation, no free repair, respect for
`mobGriefing`, and no replacement of occupied blocks. Long-duration
colony behavior and GUI persistence still require actual playtests.
