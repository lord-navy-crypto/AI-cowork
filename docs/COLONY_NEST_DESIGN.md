# Swarm Mobs — social-insect colony and nest architecture

## Biological inspiration (NOT literal reproduction)

Sources:
- Beshers & Fewell, *Annual Review of Entomology* (2001), DOI: 10.1146/annurev.ento.46.1.413 — response thresholds and decentralized division of labor
- Johnson, *Behavioral Ecology and Sociobiology* (2010), DOI: 10.1007/s00265-009-0874-7 — flexible division of labor in honeybees
- Khuong et al., *PNAS* (2016), DOI: 10.1073/pnas.1509829113 — ant nest construction from local stigmergic cues

Treat the colony as a **distributed state**, not a queen issuing all tasks.
A physical Nest Core is a durable environmental cue: neighbors can later
learn the nest location, respond to its local task demand, and coordinate
without giving an omniscient controller access to the whole world.

## Implemented in this prototype

- `swarmmobs:nest_core`: registered world block, saved by vanilla chunk
  blockstate storage. No always-ticking BlockEntity, no additional
  server-side chunk tickets, currently visualized with a vanilla mud-brick
  texture and a low light level.
- An **optional**, OFF-by-default idle-colony Goal on Zombies. It only runs
  with the swarm master enabled, with mobGriefing enabled, with no player
  target/movement plan, and when a small mixed-species local group exists.
- Site survey is throttled per worker (default >=200 ticks) **and**
  dimension-wide (no more than one site survey every 40 ticks).
- Founding places ONE Nest Core in existing air above natural soil, without
  digging or replacing a non-air block. Nearby loaded nest cores block
  additional local founding. It avoids construction within 8 blocks of
  any living player. All world reads guard against unloaded chunks.
- A new event counter tracks successful core placements by currently loaded
  agents. Server Control Panel -> Coordination exposes opt-in controls,
  population minimum and survey interval.
- A pure idle caste policy defines flexible biases: Zombie worker,
  Skeleton guard, Spider scout, Creeper reserve. The *worker* build action
  is implemented; other colony jobs are future work. Existing combat goals
  and per-target tactical role specializations take precedence.
- An idle fast path skips expensive neighbor scans for targetless agents in
  an entirely player-empty dimension, polling once per second rather than
  spending every sixth tick on needless target coordination.

## What is deliberately NOT implemented yet

- No new natural world generation, spawner, nest mob production, or resource
  duplication.
- No active storage, guard patrol, scout delivery, repair, recruitment,
  pheromone trails, queen agent, or nest block entity.
- No cross-chunk colony network or permanent worker ownership. Cores persist
  as actual blocks, but founding counters are runtime diagnostics only.
- No promise of FPS/TPS improvement without independent spark profiling.

## Suggested stages

1. **Founding / safety gate (this version)**: very rare opt-in cores,
   minimum colony population, natural soil and player-protection checks.
2. **Nest lifecycle**: a strictly bounded colony registry tied to loaded
   cores; discover/claim/repair with bounded task leases, no chunk tickets.
3. **Worker feedback (stigmergy)**: environment signals invite workers to
   extend pre-approved, finite nest footprints. Require real materials,
   world permissions and reversible placement. No invisible free blocks.
4. **Flexible caste demand**: guards/scouts/carriers respond to colony needs
   and return to baseline combat whenever a valid player target appears.
5. **Experiments**: compare idle population stability, nest duplication,
   aggregate world-query counts, MSPT/P95 and response to communication faults.

## Developer checklist

- Minecraft 1.21.1 / NeoForge 21.1.x / Java 21.
- Test with `nestConstructionEnabled=false` first (safe default).
- Back up a test world before opting into block placement.
- Test `/gamerule mobGriefing false` and both single-player and dedicated server.
- Validate 10 / 50 / 100 / 200 mobs with equal terrain/player conditions.
