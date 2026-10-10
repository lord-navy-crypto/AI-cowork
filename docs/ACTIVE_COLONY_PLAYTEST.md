# Swarm Mobs — Active Colony Playtest (isolated test branch)

Branch: `test/colony-all-features-playtest-20261010`, based on PR #64 commit `b4a1ccad`.

**Use a disposable creative test world.** All destructive features remain OFF by
default in both new and old worlds to avoid interference with GameTests and existing
builds. Use the explicit testmode command below to enable everything for testing.
These features can break logs, soil, crops, farm animals and player-built blocks.

## 1. Enable all colony experiments

Run as an operator in your disposable test world:

```mcfunction
/swarmmobs debug testmode on
/swarmmobs debug testmode status
```

The runtime toggle enables nest founding, nest lifecycle, physical resource hauling, block/crop
gathering, crop replanting, renewable berry harvesting, animal hunting, pheromones,
adaptive stock/recruitment and visible shell expansion, plus baseline swarm coordination,
engineering, communication, pathfinding and particles. It also enables the
`mobGriefing` and `doMobSpawning` gamerules **for that world**. It shortens bounded
worker survey intervals to make active labor easier to observe.

External Ollama AI intentionally stays disabled: no model/backend is guaranteed,
and it is not needed for the deterministic swarm controller. Fault-injection
rates remain zero for a clean behavioral baseline.

To disable destructive colony behaviors afterward:

```mcfunction
/swarmmobs debug testmode off
```

**Important:** `off` does NOT restore world gamerules or prior numerical survey
intervals. Restore `/gamerule mobGriefing false` manually if that was your
earlier value. Do not open important worlds with this branch.

## 2. Actually give Zombies a job (not just spawn mobs)

A plain Zombie has no economic task without a loaded and active *Nest Core*.
Creative/Spectator players are NOT hostile targets, so a group can legitimately
wander and look idle. In Survival, mobs with a visible player target prioritize
combat, not resource collection.

Recommended controlled setup:

1. Use a new flat/grass test world on Normal difficulty with operator commands.
2. In Creative, `/give @s swarmmobs:nest_core` and place a Nest Core on an open pad.
3. Spawn 3–6 ordinary Zombies about 4–8 blocks from the core. Let the core perform
   a colony cycle (staggered, about once per 200 ticks / 10 s at 20 TPS), which
   can enroll nearby idle workers.
4. Stand **roughly 18–25 blocks from the core**: close enough to keep its 48-block
   observation gate active, but away from workers and resources. The nest has
   a 12-block player exclusion zone for births; some work sites also have
   player proximity protections.
5. Give workers something real to do: place ordinary dirt/log blocks and ripe
   crops within their short local survey range (the gathering scan is only
   about 4 blocks horizontally); put a few real *dropped* food/log/dirt item
   entities near workers but outside the core's 3-block direct pickup radius.
   For animal-hunting trials, provide adult farm animals outside player
   exclusion zones and use an enclosed test area.
6. Observe for several survey cycles. Run:
   ```mcfunction
   /swarmmobs debug workstatus
   /swarmmobs inspect
   /swarmmobs group
   /swarmmobs panel
   ```
   `workstatus` reports the nearest Zombie's saved home, whether it is
   loaded and is still a valid Nest Core, its distance to that home,
   target/combat gate, engagement mode, job switches and mobGriefing.

A worker **cannot harvest resources remotely**, generate invisible food,
invent a Nest Core, or take work while fighting. A visibly idle Zombie with
`home=none` has *not yet been enrolled* and will not run home-bound tasks.

## 3. Separately verify coordinated combat

For combat, **switch to Survival/Adventure** in the same disposable world,
then run `/swarmmobs debug spawn 8` and `/swarmmobs inspect`.
The default direct player detection radius is 32 blocks with real line-of-sight.
Do not confuse creative-mode non-targeting with a swarm AI failure.

## 4. Expected and NOT yet verified

What the code can do: combat target relay, bounded local search,
colony enrollment, resource collection/delivery, reproduction and optional
visible shell placement.

What is **not** guaranteed by passing GameTests: robust autonomous behavior
on every terrain, continuous 20+ agent navigation, long-term realistic
self-sufficiency, full underground excavated hives, or a successful
multi-minute economic loop in this playtest. Test real outcomes and failures.

## 5. Local testing

This repository intentionally has **no `gradlew`** script in the reviewed
revision. CI uses Java 21 and **Gradle 9.2.1**.

```bash
cd "/Users/jason/Desktop/MinecraftDev/SWARM-MOBS"
git fetch origin test/colony-all-features-playtest-20261010
git status --short
# Only if the worktree is clean:
git switch -c swarm-colony-active --track origin/test/colony-all-features-playtest-20261010
gradle runClient
```

Avoid `git reset --hard` or `git clean -fd`: keep unrelated local work.
