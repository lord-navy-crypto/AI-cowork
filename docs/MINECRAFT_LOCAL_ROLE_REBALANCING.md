# Minecraft Swarm Mobs — Local Role Coverage (vacant flank recovery)

This is a game-only NPC role reassignment mechanism, complementing the
existing movement-aware SWEEP/SURROUND/SEARCH behavior and the Nest systems.
It does not add new weapons, damage, special attacks, or a global commander.

## What problem is solved?

Previously, formation slots were allocated *within each mob capability
group*. This keeps Skeletons, Spiders, Zombies and Creepers independently
stable, but could leave a missing flank when a Spider died, got separated,
or reassigned; Zombies might continue their prior rear/front role.

Each active mob already holds a local list of same-target peers. The new
`SwarmLocalRoleCoveragePolicy` uses that exact set — it never scans
additional entities — to check which Spider flank roles are *actually*
observed rather than relying on expected composition alone.

If local coordination is enabled, target confidence is adequate, a combat
group is active, and >=2 eligible assault Zombies are present:

- Reserve one assault Zombie (deterministic UUID rank) as the front
  CHASER, so the entire pack does not abandon direct pursuit.
- Use additional eligible Zombies to fill missing FLANK_LEFT and/or
  FLANK_RIGHT game positions. A surviving left-flank Spider leads to
  right-flank reinforcement; a right-flank Spider leads to left coverage.
- No reassignments are issued to Skeletons, Spiders, Creepers, or to Zombies
  committed to ENGINEERING or MATERIAL tasks.
- React faster to an actual vacancy with <=8 ticks of role hysteresis;
  ordinary role changes keep the configured hysteresis. No instant
  teleportation and no bypass of Minecraft pathfinding.
- A new target or exit from a coordinated encounter stops the previous
  coverage assignment. This is an NPC role, not a queued world edit.

Real diagnostic data:

```mcfunction
/swarmmobs inspect
/swarmmobs group
/swarmmobs panel
```

Inspect reports `fillingMissingFlank` and `flankFillEpisodes`. Group
and Command Center report active flank fillers and historical episodes
from actual server-side state, not a UI preview.

## Test scenarios

Use a disposable Minecraft survival test arena. Start with several
Zombies, two Spiders and one Skeleton/Creeper. Compare the paths as Spiders
move away or are removed, one at a time; do not conclude success from
the printed role alone. Verify that the relevant Zombie adopts a
different planned destination and *travels* toward it.

Repeat with only one Zombie, with three Zombies and no Spiders, and with a
Zombie carrying an active engineering job. Observe whether jobs remain
protected, the direct CHASER remains, and different flank roles are selected.

When the target becomes unobservable or the group dissolves, normal SEARCH,
role hysteresis and autonomous work take precedence.

## Automated verification and limitations

The pure policy tests verify front retention, left/right vacancy fill,
loss of Spider coverage, native Spider coverage preservation, committed
workers, singletons, disabled coordination, UUID-order determinism, and
deduplicated local peer lists. State tests verify real episode counting and
reset on target change. Existing Minecraft Runtime GameTests are required
as an integration regression suite.

Small differences in which nearby members each mob can perceive may create
temporary local disagreements; this is expected in a decentralized model
and should be evaluated in real world playtests. All role changes still
depend on underlying Minecraft navigation and attack handoff.
