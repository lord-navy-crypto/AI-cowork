# Development playtest defaults and persistent Command Center settings

Development branch starts **all existing physical colony experiments enabled by
default for NEW worlds**: founding, lifecycle, hauling, block and crop gathering,
crop replanting, berry foraging, animal hunting, visible shells. The baseline
swarm controller, local communication, engineering and division of labor are ON.
External Ollama/active AI and injected noise/dropout are deliberately not
auto-enabled; they require separate setup and can invalidate baseline behavior.

**These world-changing experiments can damage player-built blocks, crops and
animals. Use only a disposable/backup world.**

NeoForge SERVER settings live under
`run/saves/<world name>/serverconfig/swarmmobs-server.toml` (development
singleplayer; exact run folder depends on Gradle configuration).

- NEW world: colony switches have true defaults during normal `gradle runClient`.
- EXISTING world: its saved false switches override defaults. Use
  `/swarmmobs debug testmode on` to enable the complete deterministic colony
  test suite. The command now saves serverconfig to disk.
- To disable destructive experiments: `/swarmmobs debug testmode off`.
  This does not revert gamerules modified by `on`; check those separately.
- Runtime GameTests use the `swarmmobs.gametest` JVM property in the
  `gameTestServer` Gradle run, intentionally defaulting destructive features
  OFF to avoid cross-test interference. This does not affect the normal
  development client.

## Save and verify an actual setting

1. In a disposable world with cheats/operator status, open
   `/swarmmobs panel`.
2. Change exactly one setting, e.g. `Nest hauling` ON/OFF or a numeric
   haul interval; click the Cloth Config **Save** button, not Cancel.
3. Close/reopen `/swarmmobs panel`: the server snapshot should display the
   new value. Change a second setting independently.
4. Exit and re-enter the *same* world, reopen panel: both selections should
   persist in serverconfig, not revert to built-in defaults.
5. Test multiple simultaneous changes (numeric + toggles), then repeat after
   re-entering the world. Conflicts with a simultaneously selected
   “Restore baseline” action are not a supported workflow; use restore alone.
6. Reopen as a non-operator in a multiplayer test: controls should be
   read-only and server should reject mutation packets.
7. Inspect `/swarmmobs debug workstatus` and `/swarmmobs inspect` to
   distinguish real work blockers from unsaved UI values.

The new Cloth Save transport sends absolute values, not stale-snapshot
toggles or deltas. It is server-authoritative, bounded to the per-setting
ModConfigSpec ranges, and persists via `SwarmConfig.SPEC.save()` before
returning the server snapshot. Existing legacy delta actions remain for the
older control screen and debug commands.

This is a code-level contract. CI verifies compile + existing JUnit and
GameTests; a human still needs to do the actual “Save → reopen → restart”
GUI acceptance test.
