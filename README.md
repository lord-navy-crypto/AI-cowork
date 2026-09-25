# AI-cowork — Future Multi-Agent Architecture

This branch preserves the experimental multi-agent work, but the architecture is now intentionally GitHub-native.

## Agents

- **ChatGPT.app** — works only on `agent/chatgpt`
- **Cursor Web** — works only on `agent/cursor`
- **coordination** — shared append-only message bus

DeepSeek is no longer part of the design.

## Core rule

Agents do not communicate by controlling each other's UI.

Every run must:

1. Read the other agent's latest commits.
2. Read new messages in the `coordination` branch.
3. Leave a review/advice message in `coordination/messages/`.
4. Only then continue work on the agent's own branch.
5. Commit only to its own branch.
6. Leave a status message with the resulting commit SHA.

## Branch permissions

```text
ChatGPT:
  READ   main
  READ   agent/cursor
  READ   coordination
  WRITE  agent/chatgpt
  APPEND coordination/messages

Cursor:
  READ   main
  READ   agent/chatgpt
  READ   coordination
  WRITE  agent/cursor
  APPEND coordination/messages

main:
  read-only for both agents during normal work
```

Historical coordination messages are immutable.

## Architecture

```text
                    GitHub
                      |
        +-------------+-------------+
        |             |             |
 agent/chatgpt   coordination   agent/cursor
        |             |             |
   ChatGPT.app    message bus     Cursor Web
        |             |             |
        +-------------+-------------+
```

The local AI-cowork helper should enforce rules, not shuttle prose between windows.

Its future responsibilities are:

- verify branch ownership;
- verify the agent reviewed the other branch before starting;
- verify a coordination review message was created;
- observe commit/status state;
- optionally run local Git/build/test helpers when needed.

## Integration

Neither agent merges itself into `main`.

Integration is a separate explicit step after review.

## Current product mainline

The repository's `main` branch remains focused on the ChatGPT Supervisor. This future branch is intentionally separate until the GitHub coordination workflow is reliable.
