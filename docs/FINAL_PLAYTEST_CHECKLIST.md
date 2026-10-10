# SWARM-MOBS — final manual playtest checklist

**Target:** Minecraft 1.21.1 / NeoForge 21.1.x / Java 21 (check the
current build and mod dependency versions). This checklist tests the
current open PR, not an already released build. Test in a BACKUP/NEW
world, never a valued survival world.

## 0. Safety and configuration

- Keep **NEST_CONSTRUCTION**, **NEST_LIFECYCLE**, **NEST_HAULING**,
  **NEST_BLOCK_GATHER**, **NEST_ANIMAL_HUNT**, **NEST_BERRY_FORAGING**
  and **NEST_VISIBLE_EXPANSION** disabled until each corresponding
  test. These opt-in features can alter world blocks and animals.
- Use the **Coordination & Labor** screen. Verify master swarm
  enablement, world gamerules (`mobGriefing`, `doMobSpawning`), and
  Peaceful difficulty are not accidentally preventing the intended test.
- Keep **adaptive stock** enabled for stock-balance testing. Its real
  item intake admits only a category's current shortage and leaves
  unused items physically in the world.
- Observe a Nest Core within **48 blocks** for colony cycles; do not
  stand within **12 blocks** when testing automatic births or optional
  visible construction. Cycles are staggered, roughly once every
  **200 game ticks** per loaded core.
- Do not use creative-spawned 100+ mobs as proof of server scalability:
  measure TPS/MSPT separately in repeated trials.

## 1. Physical resource accounting

Place a Nest Core on a clear test pad. With lifecycle enabled, place
actual dirt, raw log and food **item entities** within 3 blocks.
Confirm that the inventory and science readout record independent
SOIL/TIMBER/FOOD points, that surplus items remain in-world when
their category target is reached, and that unrelated items are never
converted into food. A core's maximum total stored resource points
is **128**. A single log is worth 3 points, dirt 1, edible food 4.

Expected: no duplication, no invisible food, no 64-log stock takeover.
Check the **adaptive stock** switch ON/OFF separately: OFF preserves
the simpler finite-capacity intake behavior.

## 2. Worker logistics and scout sensing

Enable hauling; allow a home-assigned Zombie to encounter an actual
dropped log or food item. Watch the **same physical item** travel
toward the loaded home and disappear only after a successful deposit.
Interrupt a carrier with a real target; the item must remain in the
world and be reclaimable later. With Spiders active, watch bounded
local sightings and short-lived work-board reservations.

Expected: worker trips and delivered-item counters change only after
successful delivery. STOP feedback may reduce repeated attempts on
unreachable routes; no chunk-loading or virtual cargo creation.

## 3. Growth and role recruitment

Feed the nest with actual food while staying outside the 12-block
no-spawn zone. Birth requires at least **12 nutritional points**
(three ordinary 4-point food items), an available population slot,
a valid nearby ground position, enabled `doMobSpawning`, and
non-Peaceful difficulty. Birth cooldown is **1200 ticks**.
Verify newly born Skeleton guards hold a vanilla bow and retain their
native ranged attack Goal. Worker/scout/reserve births should preserve
their native mob behaviors.

Expected: births consume FOOD, never soil or timber; population stays
under the nest's configured local capacity.

## 4. Chamber expansion

Near capacity, supply **8 soil points + 6 timber points** for one
new chamber; confirm capacity rises by **four**. Optional physical
shells use only small dirt/log pieces and require `mobGriefing`.
Block one planned shell site and verify no material charge or
replacement occurs. With shell visualization OFF, virtual chamber
capacity still consumes real materials.

Expected: no free expansion, no excavation, no overwriting a player
block, no automatic chunk tickets. This is a conservative external
shell, **not** an excavated 3D honeycomb/tunnel system.

## 5. Adjacent nests and ownership

Place two nearby Nest Cores and assign a Zombie/Skeleton to one home.
Check that the other core does not count that assigned worker toward
its own population or birth capacity. Destroy a **loaded** old core:
rehoming is allowed after confirming its absence. Unloaded foreign
homes must not be treated as destroyed.

Expected: no duplicated census, no cross-colony labor theft.

## 6. Combat/work handoff and optional support geometry

Allow ordinary Zombie, Skeleton, Creeper and Spider combat to occur.
Check that work stops under a genuine threat and resumes only after
the alert/combat/recovery cycle. Check Skeleton bows, Creeper fuse and
Zombie melee/engineering independently. Toggle **Local
support-position optimization** ON/OFF to compare safe support-lane
selection. There is **no turn-based combat subsystem**.

Expected: native attacks remain real-time; no invented formation
rotation or damage multipliers.

## 7. Performance and evidence

Run repeatable trials with the same seed/terrain, roughly 8, 16 and
32 active mobs (subject to your machine). Record:
- MSPT/TPS, peak frame drops, and any server exceptions;
- actual successful deliveries per five minutes and abandoned leases;
- real births, resource consumption, population cap and shell levels;
- stuck path attempts, unnecessary lane switches, and ability to
  resume work after threats;
- whether loaded neighboring cores retain exclusive census ownership.

Record the exact build commit, mod configuration, gamerules, seed,
mob count and test duration with each report.

## Known limits

- Minecraft Runtime GameTests validate specified code paths, not
  long-duration autonomous survival or optimal performance.
- Resource pheromones are sparse digital coordination signals, not
  physically measured chemical diffusion.
- Shell growth is a tiny block-level extension; subterranean
  architecture, self-excavating honeycombs and full colony husbandry
  are **not implemented**.
- Real-world robotics terms are design inspirations, not a claim
  that this game mod implements certified MPC, ORCA or collision-free
  multi-robot navigation.
