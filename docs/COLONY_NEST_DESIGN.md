# Swarm Colony Core — insect-inspired autonomous monster ecology (prototype)

## Design

This is **not** a literal beehive/ant nest. The `swarmmobs:nest_core` is a
fictional monster-colony signal node with original slate/amber/violet
pixel-art textures, a simple stored-resource economy, and bounded
population production. Ant stigmergy and bee task plasticity inspired
the design; no all-knowing queen issues individual movement commands.

Research background:
- Beshers & Fewell (2001), *Annual Review of Entomology*,
  https://doi.org/10.1146/annurev.ento.46.1.413
- Khuong et al. (2016), ant construction and local stigmergic cues,
  https://doi.org/10.1073/pnas.1509829113

## Current game functionality

### Safe founding

- Idle, targetless Zombie workers in a group of at least three may create
  a persistent core on natural dry soil. Founding is OFF by default and
  follows `mobGriefing`. It does not break or replace non-air blocks,
  place blocks inside living entities, force-load chunks or build near
  players. Both per-Zombie and per-dimension survey cooldowns limit load.
- A player can obtain a core in test worlds with
  `/give @p swarmmobs:nest_core`.
- The core registers a block item and a BlockEntity saved to ordinary chunk
  NBT. Its own work happens once per 200 server ticks (staggered by position)
  only when the separate **nest lifecycle** option is enabled.

### Custom textures

Gradle task `generateNestArtwork` creates 32x32 **original** PNGs:
`nest_core_side.png` and `nest_core_top.png`. No vanilla mud-brick
texture is used. Both are included in the mod JAR with the block and item
models; GitHub CI explicitly inspects the resulting JAR contents.

### Material-conserving resource intake

The core accepts only existing dropped items within three blocks:

| Input | Resource points |
|---|---:|
| Dirt / coarse dirt / mud / gravel | 1 |
| Minecraft item-tagged logs | 3 |
| Rotten flesh / bone / spider eye / raw meat | 4 |

Capacity is 128 points. Partial fractional items are not consumed: if
remaining capacity is 2 and an item is worth 4, no part of that item is
accepted. Accepted stacks shrink by the exact whole-item count.
**No free material creation, automatic terrain strip-mining or animal
hunting has been implemented.** Manual item delivery currently works; a
physical worker-hauling goal is a later increment.

### Capped colony production

Each produced normal mob costs 12 stored resource points. Production
rotates among **vanilla Zombie, Skeleton, Spider and Creeper** with at least
1200 game ticks between successful births per core. Production occurs
only when the nest is loaded, a non-spectator player is within 48 blocks
but no player is within 12 blocks, `doMobSpawning` allows it, difficulty
is not Peaceful, safe dry space exists, and the locally counted monster
population remains below `nestMaxPopulation` (default 12; max 32).
The core uses no chunk tickets. It does not override vanilla entity combat.

### Leaders and flexible professions

Each core may select one already nearby Zombie and one Skeleton as
persistently named **Colony Regent Zombie / Colony Regent Skeleton**,
using an ordinary entity persistent-data marker and visible name.
These are symbolic leadership roles, not new independent monster
species, royal biology, or an omniscient control AI. Persistent core
leader marks prevent repeated appointments just because one walks away.

A separate pure policy models flexible idle task **biases**:
Zombie = worker, Skeleton = guard, Spider = scout, Creeper = reserve.
Only the Zombie construction job is implemented; guard patrols,
resource-hauling trips, scout discoveries, and breeding chambers are
planned, not yet implemented.

## Developer and player precautions

- Both `nestConstructionEnabled=false` and
  `nestLifecycleEnabled=false` by default. Users must explicitly opt in.
- Controls: Command Center -> Coordination -> nest construction, colony
  lifecycle and maximum local population. `baseline_all` disables both.
- Existing world's blocks never change merely because the mod is installed.
- Back up test worlds before enabling dynamic world modifications.
- Validate single-player and dedicated-server behavior; all Java-side
  lifecycle data is owned by the server.
- Keep 10, 50, 100, 200 mob MSPT/P95 performance experiments as a separate
  acceptance requirement; passing GameTests does not quantify FPS gains.

## Next roadmap

1. End-to-end mob production and NBT persistence runtime checks.
2. Worker-hauling tasks with real cargo, cooldowns and rollback on failure.
3. Local nest maintenance and room-module building that **spends** stored
   material and respects protected world areas; never infinite excavation.
4. Distance-weighted scout/guard tasks and environmental pheromone TTL.
5. Clear operator readout for inventory, named regents and local population.

## Colony Science Lab — measurable ant/bee-inspired model

Published scientific context:
- Beshers & Fewell (2001), *Models of Division of Labor in Social Insects*,
  https://doi.org/10.1146/annurev.ento.46.1.413
- Khuong et al. (2016), *Stigmergic construction and topochemical information
  shape ant nest architecture*, https://doi.org/10.1073/pnas.1509829113
- Khoury et al. (2013), *Modelling Food and Population Dynamics in Honey Bee
  Colonies*, https://doi.org/10.1371/journal.pone.0059084

Our formula is an **inspired game-scale heuristic**, NOT a calibrated model
of actual insect population biology. It deliberately simplifies complex
queen/brood/worker life stages into vanilla Minecraft mob recruitment.

Each loaded, opt-in, active nest samples its own local 14-block population
every 200 game ticks and records:
- local count by Zombie-worker / Skeleton-guard / Spider-scout / Creeper-reserve;
- current, peak, population delta per sample, and EMA (alpha = 0.25);
- current actual capacity, core/chamber level and 0..1 occupancy;
- food/nutrient readiness, cumulative births and demand indices;
- saved points of soil, timber, nutrient/biomass and historical legacy stock.

Per-role demand uses the monotone response-threshold function

    R(s, theta) = s^2 / (s^2 + theta^2)

where s is each role's projected workforce deficit for the NEXT member.
Within `nestWorkerTargetShare`, `nestGuardTargetShare`, and
`nestResponseThreshold` bounds, the highest response selects the next
Zombie/Skeleton/Spider/Creeper. Users may opt for the old round-robin
sequence by disabling `nestAdaptiveRecruitment`.
If operator ratios accidentally exceed 80% combined, they are normalized
so scout and reserve demand cannot be erased.

**Resource conservation update:** current soil, timber and nutrient
points are independently saved to NBT; the old generic Resources total
is restored as explicitly labelled legacy supply, not silently recast
as meat. Fresh soil and timber CANNOT fund reproduction. Only nutrients
or preserved legacy stock can pay the 12-point spawn cost.

**Nest architecture:** the core has an abstract, persistent chamber level.
A new core starts with capacity four. When its nearby population reaches
at least current capacity minus one, one upgrade spends exactly eight
soil points and six timber points. Each completed abstract chamber adds
four local capacity, never exceeding the configured global hard cap.
This is intentionally a simulation of internal room construction: **no**
automatic external block placements, underground excavation or terrain
modification accompany it yet.

The Command Center shows the **last active core sampled in that dimension**.
It is NOT a dimension-wide sum; data carries sample age and coordinates
so stale observations and multiple nests are not conflated. It does not
scan unloaded chunks or add a per-tick global registry.

### Acceptance criteria

- New core: capacity four, zero soil/timber/nutrients; no free recruits.
- Stock is conserved, categories can't be interchanged, capacity is capped.
- Soil-only nest cannot reproduce, nutrient-fed nest can only reproduce
  under existing cooldown/population/world-safety gates.
- A real three-member group with eight dirt and two logs can construct one
  abstract chamber, reaching capacity eight and spending all materials.
- Different mixes of actual mobs cause measurably different next recruits.
- Single GameTest server regression and JAR build must pass; this is NOT
  a substitute for 50/100/200-mob real MSPT benchmarking.

## Physical nest expansion (new opt-in experiment)

Scientific inspiration: Khuong et al., *PNAS* 2016,
https://doi.org/10.1073/pnas.1509829113 demonstrated local material
deposition and stigmergic feedback in ant nest construction. The Minecraft
mechanic below is **not** a biologically calibrated reconstruction.

When `nestVisibleExpansionEnabled=true`, the core attempts to express
each newly completed abstract chamber as a pair of **actual world blocks**:

- One directly placeable dirt block and one unstripped oak-log support: neither
  block requires a Zombie to craft mud bricks, planks, or strip bark.
- Positions are deterministic within a two-block-radius footprint.
  Up to four chambers fill eight positions at ground level; subsequent
  chambers place supported upper-tier pieces (14 maximum pieces).
- Existing `SOIL_COST=8` and `TIMBER_COST=6` resource points finance both
  the room-capacity increase **and** its two visible representatives.
  Materials are never billed twice.
- Every proposed position must already be loaded, within world bounds,
  completely air/dry, without a living entity, and on a natural solid
  foundation (upper tier must stand on already constructed shell).
- Both candidates are checked before changing the world; construction
  respects `mobGriefing` and is rejected near players. Existing blocks
  are NEVER replaced, and a failure leaves the room level and resource
  stores intact. No excavation or new chunk loading takes place.
- The visible construction option is OFF by default, independently of
  `nestLifecycleEnabled`. When enabled on an old core that previously
  paid for abstract rooms, at most one already-paid room is visualized
  per 200-tick colony cycle with no second resource charge.
- Control Panel -> Coordination shows both actual *visible shell level*
  and the internal *abstract chamber level*. Obstructions can cause an
  intentional mismatch; this is not automatically treated as a bug.
- Scope is intentionally a compact **prototype mound/shell**, not a
  functional tunnel network, an excavated chamber or a large beehive.

### Distinct resource purposes

- **Soil:** structural masonry and future corridor foundations;
  does NOT increase population or count as food.
- **Timber:** load-bearing frames and planned storage/maintenance modules;
  does NOT increase population or count as food.
- **Meat/organic nutrients:** new-colony-member production and future
  brood/nutrition pressure; does NOT substitute for building materials.

We explicitly separate the current measured engineering ratios (game design)
from ant/honeybee experimental biology. A future 'forager' should physically
collect item drops in loaded chunks and deliver them, with no duplicate
item creation or forced animal hunting/excavation.

## Raw-logs/no-crafting correction

A Zombie's current capabilities must not imply access to the player's 2x2/3x3
crafting grid. The nest accepts actual item-tagged logs (`ItemTags.LOGS`);
logs count as 3 timber points per log. The visible structural supports now
place ordinary `minecraft:oak_log`, not `stripped_oak_log`, and the soil
member is ordinary `minecraft:dirt`, not crafted mud bricks.

Existing chamber costs are unchanged: eight soil points plus six timber points
(two actual log-item equivalents) finance a four-capacity chamber and its
visible compact two-block representation. The consumed resources also stand
for implicit foundation/building costs; it is not a one-block-for-one-item
world blueprint. Wood species are pooled into a generic timber resource, so
the new raw-log support visually defaults to oak even if supplied with
another log species. A future material-provenance system can make these
exact-species supports without silently changing stored legacy inventories.

Important distinction: the nest already **absorbs nearby dropped log items**
but Zombies are not yet implemented as reliable log collectors with
transport and delivery. A future worker task can implement all three
steps and produce telemetry, without granting them a hidden crafting API.
