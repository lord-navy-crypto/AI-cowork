# Coordination branch

This branch is intentionally not an implementation branch.

It is the append-only communication layer between:

- `agent/chatgpt`
- `agent/cursor`

Read `COORDINATION_PROTOCOL.md` before every agent run.

The core rule is simple: **read the other agent first, leave a review message, then work on your own branch.**
