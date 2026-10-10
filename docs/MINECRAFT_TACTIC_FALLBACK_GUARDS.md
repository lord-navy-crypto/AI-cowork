# Minecraft Zombie & Skeleton — attack-preserving movement fallback

## What is fixed

The previous positioning iteration changed real Minecraft MOVE/attack handoff:
Zombies could finish a short flank before vanilla melee and Skeletons could
find a supported game square outside their comfort distance or behind the
front-line's arrow corridor.

There were two edge cases:

1. A Skeleton could repeatedly choose a candidate square that looked clear
   in the immediate block probe but had no complete Minecraft path. As long
   as its `rangedSpacingActive` flag stayed true, it could defer shooting.
2. A flanking Zombie could keep trying to reach a nearby but blocked/unreachable
   waypoint inside the normal 3.25-block release envelope instead of handing
   movement back to vanilla melee.

## Server-side behavior

- **Skeleton:** after 30 game ticks of one optional reposition episode
  (1.5 seconds at 20 TPS), the custom movement lease expires. A 50-tick
  cooldown prevents the next AI sample from instantly rearming the same
  failed move. If the currently observed game target is visible, the ordinary
  bow bridge can resume. A known same-target ally blocking the current
  arrow corridor remains a separate safety gate: the timeout doesn't
  magically make firing through that NPC acceptable.
- **Zombie:** optional final flank movement is allowed only when its immediate
  proposed Minecraft waypoint has valid ground, free body space and lies
  inside a loaded chunk. The final movement is capped at 18 game ticks,
  followed by 30 ticks before a new optional flank can begin. At <=2.5 blocks
  from the target, the original vanilla melee handoff is always preserved.
- The cooldowns are per-target and temporary. Changing the tracked game
  target clears the old leases. The normal navigation and SEARCH routines
  continue running with their existing budgets; these timers do NOT
  introduce artificial combat rounds.
- **Avoid the failed square:** A Skeleton timeout remembers the exact
  failed Minecraft game waypoint for **180 ticks** and rejects newly planned
  optional positions within **2 blocks** of it, after the original 50-tick
  general cooldown expires. The next observed, clear side is eligible
  instead. A flanking Zombie timeout similarly remembers the failed
  square for **150 ticks** (within **1.5 blocks**) after its 30-tick
  cooldown. These are temporary, per-target local movement memories, not
  enemy-location memories; swapping targets clears them immediately.
- The game planner filters candidate waypoints **before** writing them
  into the active server-side movement plan. A rejected optional waypoint
  leaves the ordinary positioning or native attack handoff intact, so the
  Command Center does not show a fake "inactive" state with a still-active
  failed optional movement destination.
- No changes to weapon damage, attack eligibility, arrows, player targets,
  health, Creeper explosions or in-world building rights.

## Reproduce in-game

Use a **disposable** Survival-mode Minecraft experiment world.

1. Surround a Skeleton with accessible and inaccessible (wall/trap) squares.
   Observe a player approaching. Its short reposition may run initially,
   but a failed attempt must stop within about 30 game ticks and allow the
   ordinary bow once the friendly corridor is clear.
2. Put a mixed Zombie/Skeleton squad near an irregular wall. Have one Zombie
   obtain the FLANK_LEFT/RIGHT role; if its nearby flank waypoint is blocked,
   normal melee must still happen when its target is close. After a flank
   timeout, keep the failed waypoint obstructed and verify that it does
   not trigger the same optional flank immediately again.
3. Run:

```mcfunction
/swarmmobs inspect
/swarmmobs group
/swarmmobs panel
```

Read the server counters `skeletonMoveFallbacks` and
`zombieFlankFallbacks`, alongside `skeletonSpacing` and its episode
counter. They show actual timeout decisions, not UI-only numbers.

The terrain check ensures the immediate square is feasible, not that a
complete path around arbitrarily complex Minecraft terrain exists.
A successful GameTest suite doesn't demonstrate high-density battle success;
a playable runtime test remains essential.
