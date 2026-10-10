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
hunting has been implemented.** Physical dropped-item hauling is implemented
as a separate, opt-in Zombie Goal; a roaming Spider may also report drops
to its loaded home core.

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
Zombie construction and real-item hauling are implemented, and Spider
resource-drop sightings now feed an opt-in, transient job board. Guard
patrols, autonomous block harvesting and full breeding chambers remain
outside this increment.

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

1. Dedicated multi-minute **natural pathfinding** tests for distant reports
   and returns with real ground/collision geometry (existing delivery tests
   reposition workers to isolate pickup and exact accounting).
2. Safe, separately opt-in resource generation/harvesting after deciding how
   to protect player-built dirt/logs and third-party land claims.
3. Add operator telemetry for active scout leads, rejected routes and stock
   shortage decisions without increasing per-tick scan costs.
4. Benchmark 10/50/100/200 monster MSPT/TPS and real multiplayer logistics.
5. Expand construction decisions to consider stock shortages, safe sites,
   capacity pressure and competing colonies.

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

## Idle Zombie worker logistics — real dropped-item transport

This incremental mechanic is now implemented as a **separate opt-in Goal**,
`SwarmZombieColonyHaulGoal`, prioritised below engineering and close-range
combat. Setting `nestHaulingEnabled=true` additionally requires
`nestLifecycleEnabled=true`, the master swarm switch, and the
`mobGriefing` gamerule. Defaults remain OFF.

The colony core records its home position in nearby idle Zombie workers'
persistent entity NBT. No global chunk-searching hive mind is involved.
Each worker surveys existing item entities in a capped eight-block radius
by default, no more than once every 100 ticks (plus entity staggering), and
a dimension-wide survey budget allows one such scan every four ticks.

**What the worker actually does:**
1. Check its loaded home block entity, current task/target, current capacity,
   and distance from the dropped source to players; do NOT take items within
   six blocks of non-spectator players.
2. Select one eligible dropped item entity (<=16 items by default), claim
   it with a short-lived worker lease, and navigate toward its position.
3. After reaching its pickup radius, escort **the same ItemEntity** back
   to the core, updating its physical position periodically. The item remains
   observable and may still be taken by normal Minecraft mechanics.
4. After arriving, use the core's existing accounting function to consume
   only accepted real items, increase the appropriate soil/timber/nutrient
   reserve and persist cumulative *hauledItems* and *haulTrips* counters.
5. When interrupted by a player target, task disablement, despawn or timeout,
   release its claim and leave any remaining actual item entity in the world.
   No invisible carrying inventory or newly minted replacement item exists.

The system respects a maximum task duration of 260 ticks, navigation
command retries no more frequently than 24 ticks per active worker, and
a global budget of one new hauling navigation request every two ticks
per dimension. Remote nest chunks are never force-loaded.

### Still outside this increment

- Zombies do NOT yet cut trees, harvest dirt, craft planks, hunt animals or
  teleport materials from unloaded chunks.
- Item stacks larger than the configured hauling limit are ignored rather
  than split into hidden partial cargo.
- Player-set protections are simple radius/game-rule gates; third-party
  land-claim permission integrations have not been implemented.
- There is no promised MSPT/TPS gain until the system is measured with
  10/50/100/200 monsters and spark or equivalent profiling.

### Runtime checks

A real GameTest must verify that delivering two existing oak-log items
produces exactly six timber points and one successful trip, and that
interrupting a dirt delivery leaves the full dropped stack available.

### Cargo proximity and demolished-home recovery

Worker delivery checks both sides of the physical logistics contract:
the worker must arrive at its valid loaded Nest Core **and** the original
claimed dropped-item entity must still be within four blocks of the worker.
At the dock the real cargo entity is synchronized to the worker before the
core can consume it; far-away or removed item entities cannot magically
be deposited. If the worker is interrupted, the real item stays where it
physically was and its temporary claim is released.

Loaded cores can re-enroll idle Zombies if their previously assigned home
is proven destroyed in a **loaded** chunk, or if that home belonged to a
different dimension. A core never loads an old chunk to investigate a
missing home, and it never steals a worker from another valid nest.
The worker's home dimension key is also stored as ordinary persistent NBT.

This is a first logistics prototype; multiplayer item ownership, third-party
claim permissions and long-run 100+ worker navigation MSPT require further
manual validation before enabling default automation.

## Remote Spider → Zombie resource dispatch (opt-in)

Spiders keep ordinary movement/combat AI. Every 160+ ticks, an idle Spider
assigned to a **loaded** Nest Core may inspect a bounded local area for
existing drops and report up to eight eligible items. Sightings are limited
to 28 blocks from the home core. The core maintains a **transient pure-Java
scout board** with at most eight (item UUID, position, material category,
observation tick) tuples. It is **not** an inventory and is deliberately
not persisted: reloading a world clears sightings, not physical items.

One Zombie at a time may reserve a lead, with an expiring ownership lease.
The worker can follow a lead outside its own normal drop-search radius,
subject to the ordinary home-distance limit and the per-dimension move
budget. It does not consume a remotely reported item: on arrival it must
resolve the **same living ItemEntity UUID**, check player proximity,
material category, stack size and current core capacity, and then take
the ordinary physical item lease before returning home. A vanished item
causes the hint to be discarded. Expiration, unreachable paths, invalid
homes and interruptions safely end or release the task without granting
resources. Nearby directly claimed items invalidate redundant remote leads.

The dispatcher now filters reports against current whole-item point
capacity, then scores eligible drops by distance and actual construction or
nutrition shortages. This preference is a game-model heuristic, not a
biological measurement. No global item scan, forced chunk load or
out-of-range teleport is used.

**Verification status:** The 55 required Runtime GameTests passed in CI
#570 for the initial remote-dispatch handoff, including a fixture that
repositions a worker between pickup and dock to verify resource conservation.
The later shortage-ranked dispatcher and Spider-to-board publication checks
must also pass their subsequent CI run before being called verified.
**A multi-minute, fully autonomous long-distance path-following test is
still pending.** Block harvesting, woodcutting and animal hunting remain
unimplemented; adding them must be explicit opt-in and avoid damaging
player structures or bypassing claim protections.

## First autonomous resource production: renewable sweet berries (prototype)

This deliberately small first foraging increment is **OFF by default** and
separate from the already opt-in nest lifecycle and real-item hauling switches.
Command Center -> Coordination & Labor has a `Renewable sweet-berry foraging`
toggle and a bounded per-worker survey interval (200 ticks by default).

When enabled alongside `mobGriefing`, idle, targetless colony Zombies whose
loaded Nest Core is below its twelve-point nutritional readiness threshold
inspect a *fixed, small neighborhood* for a ripe (age 2 or 3) sweet berry bush.
The dimension shares a one-survey-per-12-ticks allowance. Only a worker in a
loaded area with enough whole-item storage capacity can start work. The worker
can navigate a few blocks toward the plant, stopping after a bounded time or
if it does not make sufficient progress. Combat and existing item hauling
retain higher priority.

At the bush, the worker re-checks the actual block state, all operator
switches, the loaded core, player exclusion radius (16 blocks), and item
capacity. Successful harvesting changes the existing bush to age 1 and spawns
an **actual ItemEntity** of 1 berry (age 2) or 2 berries (age 3). No hidden
materials are credited to the Nest Core. The normal existing-item hauling
subsystem must separately pick up and deliver those berries before they become
nutritional resource points. The core persists `ForagedBerries` as a count
of physically spawned berry items, distinct from `HauledItems` and
`HaulTrips`.

Important boundaries:

- This is *not* an unrestricted terrain miner. Dirt and trees are untouched.
- A player-grown sweet berry farm can still be harvested if the operator
  explicitly enables this in a nearby loaded area. There is no dependable
  third-party land-claim authorization integration yet. **Use a dedicated test
  world** and keep the feature disabled on public/multiplayer servers.
- Harvest happens only with `nestBerryForagingEnabled`,
  `nestHaulingEnabled`, `nestLifecycleEnabled`, the master switch and
  `mobGriefing` all enabled; both individual colony and global baselines
  disable the harvesting switch.
- This mechanic is a small test of renewable-world-resource supply, not an
  implementation of natural tree recognition, authorized digging, or
  complete long-distance autonomous worker navigation.
- The server checks whether new item creation succeeded before incrementing
  the foraging counter; if item addition fails and the picked state is
  unchanged, it restores the plant's old state.

An end-to-end runtime fixture verifies a fully ripe bush remains planted
after picking, physical berry items exist without immediate nest inventory,
and the regular physical Zombie hauler can carry the *same* item entity into
the core for exact resource-point accounting. Long-running natural navigation
and load testing remain separate acceptance gates.

## Colony ecology expansion: hunt animals, gather dirt/logs, harvest ripe crops

The swarm colony is meant to be an **active environmental consumer**,
not just a passive collector of already-dropped items. Two new switches in
Command Center -> Coordination & Labor allow the operator to enable:

- **Animal hunting**: idle Zombie workers select real adult pigs and chickens
  first (with cows, sheep and rabbits as alternatives), claim the living
  animal against other workers, walk toward it, and attack with the real
  Zombie melee mechanic. Food arrives via **vanilla animal loot**, and the
  existing hauling Goal must separately transport the real ItemEntity. No
  virtual nutrition credit is granted for hitting or killing an animal.
- **Block and crop gathering**: idle Zombie workers may deliberately break
  soil (dirt, grass block, coarse dirt, mud, rooted dirt, podzol), raw logs,
  ripe standard crop blocks, ripe cocoa and nether wart, melons, pumpkins,
  mushrooms and grown upper sugar cane. The standard Minecraft destroy/drop
  path emits physical loot, consumed only by a later real-item delivery.
  Immature crop blocks, protected inventory-bearing blocks, liquids and
  unbreakable material are excluded from the selector.

There is **no special player-building exemption** in the new gathering
Goals. A log used in a player wall or dirt in a player's construction can
be selected as a harvest target when the operator explicitly enables the
mechanic. Animal farms and crop fields can be harvested as part of this
fictional colony ecology. Both features default OFF for existing worlds,
require lifecycle + hauling + mobGriefing and can be disabled independently.
Finite work windows, bounded local surveys, one-block work actions and
dimension-level query budgets protect server performance, not buildings.

Nutrient storage accepts any edible item with Minecraft's FOOD component,
including food from other mods that use that component, plus non-edible
agricultural resources such as wheat, seeds, mushrooms, cocoa, nether wart,
eggs and sugar cane. Food and building stock are accounted as separate
soil/timber/nutrient point categories. The colony prioritizes nutritional
shortages, then timber and soil shortages needed for nest expansion.
Workers never craft planks or create phantom materials.

Spider still searches for existing dropped resources while Zombie is the
current producer/hauler. Direct animal-scout relay, sapling planting,
tree-regrowth cycles, pathfinding over long distance, seasonal ecology and
true job auctions are future increments; they are not claims of complete
ant/bee simulation. This is a game-scale experimental model.

Integration tests cover the intended conservation gates: log block -> real
log drop, soil block -> real dirt drop, mature crop -> real edible drop,
animal melee -> real vanilla meat -> separate worker hauling -> nest points.
The full automated CI checks are authoritative for whether these increments
work on the current development head.

## Research-backed colony labor: site exclusivity + indirect feedback (v0 experimental)

This increment implements **local, distributed** labor responses, rather
than a centrally-scripted hive queen. The implementations are inspired by
published biological mechanisms; their numeric values are deliberately
chosen GAME model parameters and must NOT be interpreted as measured insect
physiology.

### Scientific grounding

1. **Response-threshold task allocation:** Beshers & Fewell (2001), *Models
   of Division of Labor in Social Insects*, Annual Review of Entomology
   46:413–440. DOI https://doi.org/10.1146/annurev.ento.46.1.413
   Internal task thresholds and external colony needs interact, and a fixed
   permanent profession is not necessary for emergent labor division.
2. **Rate of successfully returning foragers:** Prabhakar, Dektar & Gordon
   (2012), *The Regulation of Ant Colony Foraging Activity without Spatial
   Information*, PLOS Computational Biology 8:e1002670.
   DOI https://doi.org/10.1371/journal.pcbi.1002670
   Red harvester ants use the local encounter rate with returning foragers
   to regulate new foraging departures, even without global spatial maps.
3. **Stigmergy:** Theraulaz & Bonabeau (1999), *A Brief History of Stigmergy*,
   Artificial Life 5:97–116.
   DOI https://doi.org/10.1162/106454699568700
   An animal can affect later actions through local, ephemeral changes
   to the environment. Existing Spider->Nest item leads already implement
   one bounded environmental-information analogue.
4. **Honey-bee negative feedback:** Nieh (2010), *A Negative Feedback Signal
   That Is Triggered by Peril Curbs Honey Bee Recruitment*, Current Biology
   20:310–315. DOI https://doi.org/10.1016/j.cub.2009.12.060
   We borrow only the abstract concept of recruitment inhibition, not a
   claim that bees respond to Minecraft pathfinding failures.

### Implemented mechanisms

- **Exclusive work-site leases:** `SwarmColonyWorkBoard` keeps at most 24
  nonpersistent block-position+worker-UUID leases per loaded nest, expiring
  after 60 ticks unless refreshed. Miners check the board before selecting
  soil, logs or mature crops, and refresh while working. Aborted tasks
  release their site. Two miners may not both commit to the same site at
  the same moment.
- **Return reinforcement:** only `acceptHaulDelivery` of an actual living
  dropped ItemEntity reinforces its nutrient/timber/soil signal. An empty,
  invalid or merely observed item gives no reinforcement.
- **Stop/inhibition cue:** a timed-out worker gathering route or animal
  hunt raises a short-lived negative cue for the relevant material.
  Both positive and negative signals decay exponentially, with a model
  half-life of 600 game ticks (30 seconds at nominal 20 TPS).
  Positive signals reduce selection cost; negative signals increase it.
  The score is bounded so a feedback loop cannot create infinite demand.
  **Stock need and whole-item capacity checks always override cues.**
- **Local metrics:** Command Center displays claimed sites, item leads,
  successful return reinforcements, failed routes, and independent
  recruitment and inhibition cues for food/timber/soil. The data come
  from the most recently sampled loaded Nest Core, not a global census.

### Calibration and open work

Game test fixtures exercise duplicate site claims, expiration/release,
signal half-life, boundedness and true physical deliveries. There is
no assertion that 600 ticks is a biologically observed half-life.
The next empirical evaluation should track total food/log/soil collected,
failed-path fractions, trips per worker, colony births, average task queue
length, and TPS/MSPT across 10/50/100/200 loaded agents.
Natural multi-minute movement, actual preference switching under high
competition, and Spider sightings of *living prey* remain future work.

## Spatial pheromone emergence: ant-inspired local rules (experimental)

This increment adds an explicit **spatial**, short-lived signal field to the
previous colony-wide response-threshold signals. It is inspired by:
- Czaczkes, Grüter & Ratnieks (2015), *Trail Pheromones: An Integrative View of
  Their Role in Social Insect Colony Organization*, Annual Review of
  Entomology 60:581–599. DOI 10.1146/annurev-ento-010814-020627
  (multiple signal functions, positive and negative feedback).
- Czaczkes et al. (2013), *Negative feedback in ants: crowding results in
  less trail pheromone deposition*, Biology Letters 9:20121009.
  DOI 10.1098/rsbl.2012.1009 (local competition can reduce reinforcement).
- Nieh (2010), *A Negative Feedback Signal That Is Triggered by Peril Curbs
  Honey Bee Recruitment*, Current Biology 20:310–315.
  DOI 10.1016/j.cub.2009.12.060 (targeted inhibitory feedback).
- Sumpter (2006), *The principles of collective animal behaviour*,
  Philosophical Transactions B 361:5–22. DOI 10.1098/rstb.2005.1733
  (local interactions can form emergent route-level coordination).

**Important:** Ant trail pheromones and bee dance/stop signaling are different
real-world mechanisms. The mod borrows complementary computational
principles; it does not assert that bees literally use ant-style pheromone
trails or that any numerical simulation coefficients are measured rates.

### Rules actually implemented

Each loaded Nest Core owns a **128-cell maximum**, ephemeral, 3D 4-block grid
(`SwarmNestPheromoneField`) supporting separate food, timber, soil and
STOP channels. The field uses lazy exponential attenuation (400 game-tick
half-life) and a neighborhood sensing kernel over seven nearby cells,
rather than per-tick whole-world diffusion or persistent global arrays.
Sources cannot report from more than 28 blocks from their own Nest Core.
Signals reset on world reload. No biome/world/force-chunk search occurs.

1. **Scouts observe**: idle, roaming Spiders recognize real dropped items;
   when the relevant mechanic is enabled they also notice nearby living
   adult farm animals and ripe/otherwise harvestable world blocks. They
   leave *weak local resource marks*. They never create virtual food or
   break blocks.
2. **Workers prefer**: when a Zombie evaluates actual eligible local blocks
   or item drops, pheromone concentration scales the score after inventory
   need, dimension budget, claim and capacity rules. An existing source is
   still required before harvesting.
3. **Successful physical return strengthens a trail**: the hauling Goal
   samples up to 16 worker positions along the real cargo return. Only
   successful whole-item delivery credits positive route marks.
   Attempted trips and phantom items create no success trail.
4. **Failures leave temporary stop marks**: a stalled gather route or an
   invalid expired drop hint adds local inhibitory scent. Local labor
   feedback separately retains overall task-category inhibition.
5. **Gradient following**: the lowest-priority Zombie task chooses only
   one short hop toward a stronger *locally sensed* scent cell, provided
   it has real stock demand and loaded walkable ground. There is no
   all-knowing target coordinate and no long-distance teleport.
6. **Self-limiting feedback**: every cell has a bounded intensity. Scent
   capacity, source-distance checks, response score clamps and evaporative
   forgetting prevent a resource from creating a permanent, infinitely
   reinforced path.

The Command Center exposes the pheromone and gradient-following toggles,
occupied cell count and observed/reinforced/stop signal counts. Both toggles
are included in colony and ALL baseline resets; all underlying world-modifying
actions still require their own separate operator switches, currently OFF
by default.

### Tests and remaining frontiers

Pure JUnit tests cover spatial localization, half-life, STOP response and
sparse memory saturation. Runtime GameTests verify Spider notices actual
live food and mature crops without collecting them, a Zombie can read a
local gradient, and only real cargo delivery reinforces a trail.
These are discrete deterministic checks. **Not yet established:** multi-hour
10/50/100/200-mob TPS comparisons; real-world distributed optimization
optimality; robust path-following over large natural obstacles; a true
comparison against biological trail data; swarm crowding-dependent trail
deposition rates.

## Joining combat-swarm AI and pheromone ecology: one local choice equation

**Architecture:** existing tactical `SwarmTaskBidPolicy` remains solely
responsible for the high-priority target/combat/engineering state, and
`SwarmZombieColonyGatherGoal`, `SwarmZombieColonyHaulGoal`, and
`SwarmZombiePheromoneExploreGoal` retain their low-priority idle and
operator-permission checks. Rather than inventing a second global AI, all
three now **reuse** existing swarm building blocks while idle:

- `SwarmTaskBidPolicy.responseThreshold(worker UUID, MATERIAL/ENGINEERING)`
  provides reproducible *individual differences*, as in original task
  threshold-based swarm allocation.
- `SwarmColonySciencePolicy.response(s, theta)` provides the original
  mathematical stimulus-response curve, `p = s²/(s² + theta²)`.
- `SwarmAgentState.taskExperience(MATERIAL/ENGINEERING)` gives existing
  experience a small, bounded effect on corresponding resource bids.
- Existing `SwarmCongestionPolicy.countWithin` now penalizes locally
  crowded directions of pheromone-only exploration. Mining/hauling also
  snapshot a bounded number of real nearby Zombie positions during their
  already-budgeted survey to discourage resource-site congestion.
- Local physical `SwarmNestPheromoneField` FOOD/TIMBER/SOIL/STOP and
  existing `SwarmColonyLaborFeedback` adjust the SAME worker task scores.
  They do not reassign Skeleton archery, Creeper combat, or Spider movement.

For a physically valid resource candidate, the model computes

```
shortage = clamp((target_stock - available_stock) / target_stock, 0, 1)
s        = 0.12 + 0.80*shortage + 0.09*local_attraction
p        = s*s / (s*s + worker_threshold*worker_threshold)

cost = base_distance_and_shortage_cost
     * clamp(
         (1 + 0.28*nearby_workers) * (1 + 0.30*STOP_scent)
         / ((0.55 + 0.65*p) * (1 + 0.16*local_attraction)
            * (1 + 0.10*existing_task_experience)),
         0.50, 3.50)
     * existing_colony_feedback_factor
```

Lowest cost wins. **Stock-room, real-resource validation, combat state,
operator switches, claim ownership and unloaded-chunk checks still have
precedence over this heuristic.** Attraction and STOP are local signals
queried at the real source coordinate. No information is fabricated.
Within an active scent field, roughly 1/8 of UUIDs have an independent
explorer phenotype and read only 35% of resource attraction (but retain
full STOP sensitivity). This preserves a modest exploration/exploitation
split without a global leader or cross-tick random thrashing.

At the actual *delivery* event, a bounded near-dock count of other Zombies
reduces the intensity deposited along the sampled physical return route:

```
deposit_multiplier = 1 / (1 + 0.55*min(nearby_workers,8))
```

This implements a **negative feedback on positive recruitment** distinct
from STOP cues for failed routes. These coefficients are game-model
assumptions, not measured ant pheromone deposition probabilities.
The motivation is grounded in:
- Theraulaz, Bonabeau & Deneubourg (1998), *Response threshold reinforcements
  and division of labour in insect societies*, Proceedings of the Royal
  Society B, DOI 10.1098/rspb.1998.0299
- Czaczkes et al. (2013), *Negative feedback in ants: crowding results in
  less trail pheromone deposition*, Biology Letters, DOI
  10.1098/rsbl.2012.1009
- Grüter et al. (2012), *Negative Feedback Enables Fast and Flexible
  Collective Decision-Making in Ants*, PLOS ONE 7:e44501, DOI
  10.1371/journal.pone.0044501

**Verification scope:** pure JUnit tests cover increased need response,
small stable explorer minorities, no infinite feedback, congestion inhibition
and scaled physical pheromone return. One Minecraft runtime test gives a
Zombie two equally distant raw logs: a local real-source pheromone mark
should break the tie, while exactly one physical log gets mined. This does
not yet establish whole-colony self-organized route optimality in varying
natural terrain; that requires a separate multi-agent benchmark with
experimental controls.

## Scout-to-worker ecology handoff (implementation milestone)

This addresses a gap between the earlier spatial pheromone field and the
older ant/bee-like swarm labor system. Local pheromone concentration alone
could bias already-nearby harvesting, but it did not assign an observed
**living prey** or **mature crop / raw log / soil block** beyond a worker's
immediate physical scan radius.

The new `SwarmNestOpportunityBoard` provides transient, nest-local job
hints: up to 24 observations, within a 28-block home radius, expiring after
360 ticks. A worker can reserve a single task for 100 ticks, and at most
six workers may concurrently hold reports per nest. Repeated sightings
refresh the same report; stale, missing, changed or completed targets
are invalidated. The board stores only primitive positions and UUIDs,
never chunk tickets, entity references, inventories or phantom loot.

**Spider:** regular bounded local surveys still inspect actual loaded
world regions. Animal and harvestable-block sightings publish into the
same nest job board regardless of whether pheromone navigation is enabled.
An optional spatial signal is also emitted for local gradient followers.

**Zombie gatherer:** after a demand-based local block scan fails, a worker
may reserve a Spider-observed block, verify that its chunk is currently
loaded and its current block kind remains harvestable, and reserve that
block through the original exclusive work board. It navigates to the
physical position, rechecks its state and breaks one real block, releasing
all claims afterward. No world item is credited to storage until hauled.

**Zombie hunter:** after a local prey scan fails, the worker may reserve
an adult farm-animal observation, move toward the reported site and scan
there for the same living UUID. Only if the animal is physically present
and still adult and suitable can the worker use original melee behavior.
This does NOT remotely attack or synthesize animal loot.

The existing high-priority Minecraft combat, goal-selector and engineering
handoff remain intact, and all harvesting still requires operator toggles.
A shared task record is **not a central omniscient AI**: discovery,
expiration, reservations and world rechecks are local, independently
limited, and subject to labor demand.

### Nest plan completion criteria (not yet met)

| Milestone | Current engineering status |
|---|---|
| Real nest core, categorized soil/timber/food stocks | Implemented, automated tests |
| Physical resource collection/hauling + pig/chicken hunting | Implemented for selected vanilla categories, tests |
| Basic chamber growth and population cap/recruitment | Implemented experimental 200-tick-cycle model |
| Optional physical shell modules | Implemented as small bounded modules, not a full anthill |
| Spider report -> worker real-block/prey task dispatch | Implemented experimental; runtime tests cover discrete handoff |
| Spatial food/log/soil/STOP pheromone gradients | Implemented as sparse per-nest experimental signals |
| Realistic large-scale multi-chamber excavation and tunnel routing | Not implemented |
| Farm replanting, tree regeneration, husbandry, full resource renewal | Not implemented |
| Dynamic long-term nest ecology with food supply/demand equilibrium | Not validated |
| 10/50/100/200 natural mob load and TPS/MSPT controlled benchmarks | Not validated |
| Main branch/release version updated with this PR | No; PR remains open |

The goal of the plan is a **credible emergent labor simulator**, not
only an expanded combat encounter. A green CI demonstrates behavior
tested so far, not completion of all ecological milestones.

## Integrated nest economy and renewable farm loop (October 2026 increment)

This increment connects the previously separate Nest Core capacity model,
ant-like task bids, Spider report board, Zombie hunting/mining/hauling,
four-channel pheromone field, and visible shell. The aim is ONE
resource->task->physical-action->stock->birth/chamber feedback loop, not
separate modes that can accidentally conflict.

### One measured stock need for every worker class

`SwarmNestLaborEconomyPolicy.targets(population,capacity,chambers,hardCap)`
derives stock quotas, using the loaded Nest Core's *last sampled local*
population (sampled every 200 game ticks). At low capacity pressure,
an extendable nest asks for soil=8 and timber=6 resource points (one real
room bill); within two slots of its current capacity, it aims for 16
soil and 12 timber points (two bills). Food target is 24 nutrition points
(two real birth bills) when below capacity; 12 when at capacity but able
to construct another room; ZERO when full at the configured hard cap.
The quotas are heuristic GAME parameters; actual construction still
requires population pressure and spends exactly 8 soil + 6 timber,
while each spawned mob still spends 12 actual nutrition points.
No resource is ever invented by a demand score.

The `NestBlockEntity.needsResource(kind)` method is the single read
for idle Zombie miners, hunters, exploratory workers and Spider animal/
crop scouts; `resourceDeficit(kind)` also feeds the already-existing
individual response-threshold utility used by miner and hauler bids.
A new `nestAdaptiveStockEnabled` option can restore legacy fixed quotas,
without disabling independently controlled hauling/harvesting/AI modes.
The operator UI shows soil/timber/food quotas and registered built shell.

### Colony material self-preservation

A source-selection conflict was corrected: the real two-piece nest
shell is constructed from dirt and raw logs, exactly the resources that
hungry miners seek. Each Nest Core now identifies only the **specific
positions of its already completed visible modules** (max 14 pieces),
and its own scout and mining Goals reject these positions even if
harvesting player-built structures is otherwise enabled.
Unrelated wood, soil, and farms are still eligible when the operator
enables destructive gathering. No radius-based block exemptions were
added. This prevents a self-reinforcing build->mine->build loop.

### Renewable crops with physical seed accounting

A new opt-in `nestCropReplantEnabled`, OFF by default, runs only inside
an already-authorized successful Zombie block harvest:
- The worker records IDs of existing nearby dropped items *before*
  breaking the genuinely ripe crop.
- Vanilla block destruction creates the actual new seed/harvest drops.
- On suitable existing farmland, one newly dropped wheat seed, carrot,
  potato or beetroot seed is consumed to replant a juvenile crop; mature
  nether wart on soul sand is likewise supported.
- If no newly produced valid propagule exists, the site remains harvested.
- No existing player item, inventory reserve, virtual points, block
  duplication, artificial crop growth or fabricated drop is introduced.
- Berry regrowth keeps its separate existing berry-harvest path. Pumpkin,
  melon, mushroom, sugar cane and cocoa are not yet automatically replanted.

### Remaining nontrivial gaps before declaring FULL completion

The module has not been benchmarked as a full biological ecosystem.
It still lacks physical multi-chamber underground excavations/tunnels
with route planning; tree sapling planting/growth, livestock breeding and
sustainable replenishment for every food category; 10/50/100/200-worker
controlled TPS/MSPT trials; statistically meaningful multi-hour
experiments of source depletion, route recovery and task division;
and dedicated bees/ants as separate Minecraft living agent classes.
The current Spider, Zombie, Skeleton and Creeper labor roles are
*inspired by* ant/bee computational mechanisms; they are not literal
biological ant/bee castes. Passing tests only verifies their explicit
scenarios; it does not prove emergent global optimality.

## Final mixed-species combat and ecological noninterference verification

The final engineering pass prioritizes **compatibility** over new powers.
The existing supported types remain separate Minecraft mobs with their
native mechanics: Zombie melee and engineering, Skeleton vanilla bow
draw/fire, Creeper vanilla swell/fuse, Spider scouting and navigation.
All colony harvesting, real-item transport, construction and resource
recruitment remain lower-priority/idle-only behaviors; they are never
allowed to steal movement from a Zombie targeting a real player.

### Cross-species tactical cooperation already in the original architecture

- Same-target local peer observations are shared across the already-bounded
  communication channel. Zombies can relay observed targets to Skeletons
  without giving the Skeleton fake line-of-sight.
- Stable role/capability slots, experience, the worker/guard/scout/reserve
  composition, target-scope filtering and formation cohesion/separation
  produce complementary duties.
- Skeletons receive long-range standoff plans and a vanilla bow handoff
  inside their real visible attack range; Creepers retain their real fuse
  Goal and tactical movement right up to its 3-block engagement envelope.
- Zombie close-up melee, obstacle engineering and materials handoff retain
  their own high-priority guards and workload claims.

### Two conservative improvements in this pass

1. `SwarmFriendlyFireLanePolicy`: A Skeleton's planned ranged-support
   approach checks its shot corridor against *real nearby Zombie and Creeper
   allies pursuing the same target*. Where a Creeper is present, its
   breacher approach direction remains the basis of the side lanes; without
   a Creeper, the Zombie frontline supplies a similar approach axis.
   Lane selection considers both the original block collision ray check
   and a simple bounded 2-D teammate clearance. Nothing rewrites vanilla
   arrow targeting, damage, AI selectors or projectile behavior.
2. `SwarmZombieBreacherSafetyGoal`: A Zombie sharing a live Player target
   with a nearby Creeper may yield its MOVE control **only after the
   Creeper actually starts swelling or is ignited**. It takes a small
   loaded-chunk, supported-ground retreat step; the goal expires when the
   hazard/target disappears, after a strict time cap, or when the Zombie
   reaches its spot. It is otherwise completely inactive, preserving
   melee, obstacle repair, harvesting, transport and the old planner.
   Scanning occurs only for already engaged Zombies, at a five-plus-tick
   cadence, over at most five blocks. Explosion damage, fuse timing and
   Creeper powers are not modified.

### Strict regression checks

- The original actual Minecraft bow-fire and Creeper swell/fuse handoff
  GameTests stay in the suite.
- A new real Runtime GameTest checks Zombie, Skeleton and Creeper sharing
  a real target simultaneously while preserving their native/engineering
  Goal registrations; it tests fuse-triggered safety and prompt release.
- Another GameTest enables all nest economy/pheromone/harvesting switches,
  then verifies that none of the idle Zombie labor Goals can start while
  it is attacking a live Player; physical terrain remains unchanged.
- New deterministic unit tests cover cross-species firing corridors,
  teammate-safe side selection, same-target fuse gating, and deterministic
  retreat vector bounds.

**Verification boundary:** successful GameTests do not prove all natural
world combat outcomes or TPS with hundreds of monsters. A live-player
survival test with a mixed group (Zombie, Creeper, Skeleton and Spider)
should still check natural terrain, bow projectiles, ignition safety,
construction, world griefing switches, toggles and frame times.

The change stays on the existing unmerged PR branch. It must not be
silently merged into main, published as a release or enabled in the
user's live survival world without separate authorization.

## Bonus battle: optional synchronized tactical rounds (safe real-time mode)

The extra "turn-based" battle option is a **tactical planning cadence**, NOT
a replacement of Minecraft's continuous combat system. The operator-facing
`tacticalRoundsEnabled` switch is OFF by default.

- An observed real target UUID and shared game tick determine a deterministic
  `HOLD -> COVER -> ROTATE` phase, 100 ticks each. Any two members pursuing
  that same UUID compute the same phase without messaging a global master.
- The base Zombie/Creeper/Skeleton target relay and formation assignment
  remain the sole authoritative combat intents. Every mob keeps its normal
  full-time melee, bow, fuse, pathfinding and engineering action windows.
- Only the Skeleton's **planned support corridor** is influenced: HOLD/COVER
  keep the stable side, ROTATE requests the opposite side. A requested
  position must pass the existing real-world block collision ray and the
  bounded teammate-occlusion test. If only one lane is clear, that lane wins;
  if neither passes, no round-based steering is issued and the preexisting
  plan is retained. No AI may force the Skeleton to fire along a blocked lane.
- When the switch is OFF the exact preceding mixed-squad side selection is
  preserved, including all combat Goal priorities and old fallback rules.
- The model deliberately does not pause any mob for an enemy's 'turn',
  manufacture damage, make shots homing, accelerate fuses or directly
  control the vanilla bow. It is a safe, optional command-center experiment.
- Fast deterministic tests check synchronized rounds, alternate positions
  under equal clearance, and blocked-lane noninterference. The 3-species
  Minecraft Runtime GameTest runs with tactical rounds enabled to protect
  target sharing, original bow equipment, Creeper hazard movement and
  engineering Goal registration.

## Work / Alert / Combat / Recovery context integration

Research motivation: collective honeybee defense has been analyzed as
"threat detection -> defender recruitment -> attack", an episodic
division-of-labour process distinct from normal food collection.
Reference (2025): https://pubmed.ncbi.nlm.nih.gov/40109103/
The four-mode design, 20/30-tick hysteresis and 100-tick planning phases
are Minecraft GAME ASSUMPTIONS, not biological timing measurements.

| Activity | Entry signal | Allowed swarm action |
|---|---|---|
| WORK | No target evidence, recovery finished | Real-resource scouting, collection, hauling, construction, and resource pheromones |
| ALERT | Live relayed / remembered target information | Suspend labor, share and verify sensed information, retain vanilla combat rules |
| COMBAT | Direct verified target, or same-target teammate with fresh DIRECT sighting | Keep vanilla real-time combat/engineering; apply coordinated formation phase when allies share target |
| RECOVERY | Target truly lost | Brief no-labor cool-down, then resume WORK without phantom information |

Every colony worker Goal, including Spider scouting, Zombie gathering,
hauling, animal hunting, berry harvesting, nest founding and pheromone
exploration, checks the same engagement mode in addition to its prior
loaded-chunk, mobGriefing, resource and game-toggle guards. Entering
ALERT or COMBAT therefore suppresses production jobs; no forced
global survey has been introduced.

An important anti-echo guard requires fresh DIRECT peer observations
from the last 20 ticks to recruit COMBAT. A chain of forwarded reports
cannot hold a fighting state alive forever when no monster actually
sees the target. Relays alone can still generate ALERT and SEARCH.
Recovery lasts 30 game ticks after evidence disappears, stopping
work/fight task oscillation.

The existing tactical-rounds switch is on by default for NEW config
files and remains user-controllable. Its HOLD/COVER/ROTATE stages apply
ONLY during COMBAT when local peers share a real target. The phase
makes minor role-specific formation adjustments for Zombies,
Skeletons, Creepers and Spiders; safe Skeleton support lane selection
remains optional. None of this gates original vanilla attacks, fuse,
or engineering. Existing saved configurations remain authoritative.
The reset action restores a conservative, rounds-off baseline.

Tests: deterministic JUnit covers transition/recovery and no stale
combat feedback, while real Minecraft GameTests check three-species
combat transitions and worker return to WORK when the player leaves.


## Reactive tactical phases (supersedes the old fixed 100-tick clock)
**Current implementation:** A "round" is a stable local controller decision, not
a synchronized game-clock interval. Earlier descriptions of 100-tick
HOLD/COVER/ROTATE cycling above are historical and NO LONGER apply.

A same-target COMBAT agent uses already-sensed local teammates and existing
navigation telemetry. Its phase suggestion uses the following deterministic
rules, in order: no fresh direct/squad evidence -> HOLD; persistent
crowding or prior failed path feasibility -> ROTATE; mixed ranged-support
and frontline presence -> COVER; otherwise HOLD. Each new candidate
requires 12 game ticks of consistent evidence, and each confirmed phase
must last at least 20 game ticks. No random phase change, no clock
deadline and no unnecessary repeated world scans.

Rotation only changes bounded formation geometry / a skeleton's verified
safe support corridor. Path feasibility and teammate clearance remain
authoritative; no forced movement when both corridors are unsafe.
Zombies retain normal close combat and engineering, Skeletons retain
vanilla bow behavior, Creepers retain native fuse behavior. Nest work
remains guarded by WORK/ALERT/COMBAT/RECOVERY independently of phases.
The operator can turn off event-triggered phases in Coordination & Labor
without disabling ordinary swarm AI. The controller exposes live
HOLD/COVER/ROTATE counts and local confirmed phase-switch counters.

**Validation scope:** The JUnit suite tests prolonged stable scenes
without clock-driven switching, transient-noise suppression, confirmed
crowding and navigation events, minimum dwell time, phase reset on
new targets, and unchanged lane safety. Minecraft Runtime GameTests
exercise actual vanilla combat Goals and swarm coordination. These are
software regression tests, not claims that this strategy necessarily
improves combat effectiveness or matches physical military robotics.
Game-specific A/B trials are still needed to quantify path length,
replan counts, attack completion, and server tick performance.

## Current scientific controller: sampled feasible-position selection (supersedes all earlier timed rounds)

Important: Previous paragraphs on clock-driven phases, 12-tick confirmation
and 20-tick phase holds describe **superseded historical implementations**.
The code now implements sampled, local geometric decision-making, NOT
physical turn-taking. No attacking, fuse or navigation Goal is gated by a
planning cycle. Work, alert, combat and recovery remain separate.

At each already-scheduled COMBAT planning sample, a ranged agent with
a real directly observed target and same-target frontal peers may
evaluate the *two positions* proposed by the existing formation planner.
The prior world-visibility ray check and same-target occlusion check are
HARD prerequisites: an unavailable position can never win merely because
it is closer. If neither passes, no optional positioning overrides the
ordinary planner. The policy does not force a risky position.

For a geometrically admissible candidate position p, peer samples q_j
and present position x, the local objective has distance units:

    J(p) = ||p-x|| + sum_j max(0, d_clear - ||p-q_j||)

where d_clear is the preexisting configurable separation distance in
blocks. Term 1 approximates movement effort in blocks; term 2 measures
total shortfall from peer spacing in blocks. There is no bonus damage,
arbitrary score for "flanking" or 1.05x/1.10x caste radius rule.
If the prior candidate is admissible, the controller retains it unless
the alternative saves more than one agent-body width in estimated
movement/clearance cost. An invalid prior candidate is abandoned without
that threshold. Position validity, path feasibility and safety are NOT
proved by this one-step Euclidean objective: real navigation remains
handled by the existing Minecraft path planner, and this is NOT a
certified real-world controller.

HOLD/COVER/ROTATE are now interpreted as *labels of measured decisions*:
HOLD = no justified optional support position; COVER = feasible position
retained/selected; ROTATE = verified change of occupied support side.
Other castes' navigation remains under the original swarm steering and
native Goals; their COVER label describes verified squad composition,
not a synthetic command. Separate labor and COMBAT activity controls
remain untouched, and existing saved operator toggles are preserved.

The practical research comparison is sampled decision vs sampled
decision (the old deterministic slot parity baseline and the new
small finite candidate optimizer). Suitable measurable metrics include:
    - number of unnecessary lane switches per 1000 planning samples;
    - total chosen Euclidean travel and clearance-deficit proxy J;
    - actual navigation arrival and blocked-path fraction;
    - server tick cost at different agent densities;
    - loss of vanilla behaviors and violations of the no-work-in-combat
      invariant.
These must be evaluated under identical worlds and seeds. Local J
improvement in unit tests cannot by itself establish better game combat
outcomes, collision-free routes or biological/robotic validity.

Conceptual background (NOT code copied or a certification):
- Alonso-Mora et al., "Distributed multi-robot formation control in
  dynamic environments", Autonomous Robots, 2019,
  https://doi.org/10.1007/s10514-018-9783-9
- Zhang, Garg & Fan, "Neural Graph Control Barrier Functions Guided
  Distributed Collision-avoidance Multi-agent Control", CoRL 2023,
  https://proceedings.mlr.press/v229/zhang23h.html
The present mod does NOT implement model-predictive control, barrier
function optimization, convergence proofs or safety certification.
