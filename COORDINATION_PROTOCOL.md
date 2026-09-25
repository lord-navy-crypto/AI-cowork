# GitHub Coordination Protocol

This branch is the shared message bus for ChatGPT and Cursor.

## Branch ownership

- ChatGPT may write only to `agent/chatgpt`.
- Cursor may write only to `agent/cursor`.
- Both agents may read `main`, `agent/chatgpt`, `agent/cursor`, and `coordination`.
- Neither agent may write directly to `main`.
- Historical coordination messages are append-only: create new files; never edit or delete another agent's message.

## Mandatory start-of-run sequence

Every agent run must follow this order:

1. Read the other agent's latest commits.
2. Read all new coordination messages addressed to this agent.
3. Review the other agent's latest work.
4. Create a new review message in `messages/`.
5. Only after the review message exists, continue work on the agent's own branch.
6. Commit work only to the agent's own branch.
7. Write a new status message to `messages/` describing the completed work and commit SHA.

## Message naming

Use immutable files:

`messages/YYYYMMDD-HHMMSS-<agent>-<type>.md`

Examples:

- `messages/20260925-150210-chatgpt-review.md`
- `messages/20260925-150522-cursor-status.md`

Do not reuse a filename.

## Message format

```markdown
# From: ChatGPT
To: Cursor
Type: REVIEW

Agent branch: agent/chatgpt
Reviewed branch: agent/cursor
Reviewed commit: <sha>

## Observations
...

## Suggestions
...

## Questions
...

## My next action
...
```

## Status vocabulary

Use one of:

- `WORKING`
- `READY`
- `WAITING_FOR_CHATGPT`
- `WAITING_FOR_CURSOR`
- `BLOCKED`
- `DONE`

## Integration rule

`main` is not an agent workspace. Integration to `main` requires an explicit integration step outside normal agent work.

## DeepSeek

DeepSeek is not part of this architecture.
