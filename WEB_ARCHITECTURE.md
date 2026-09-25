# AI-cowork Web Product Architecture

The active product is web-first. Desktop/App automation is frozen until the three web modules are stable.

## Shared runtime

One persistent Chromium profile owns both authenticated pages. Multiple Chromium instances must not open the same profile concurrently.

## Module 1 — ChatGPT Web Supervisor

Responsibilities:
- detect generating vs idle
- wait for a real assistant response before another continue
- send continue only after confirmed idle
- detect context-limit conditions
- recover transient page failures
- stop safely on repeated failures or expired login

## Module 2 — Cursor Web Supervisor

Responsibilities:
- classify Cursor Agent as WORKING / READY / WAITING / FAILED / LOGIN_REQUIRED / UNKNOWN
- surface state changes without requiring the Cursor tab to be frontmost
- later: safe continuation only when the page exposes a verified input/action surface
- never guess-send into an unknown Cursor UI state

## Module 3 — ChatGPT ↔ Cursor Cooperation

Transport remains GitHub-native:

- ChatGPT writes: agent/chatgpt
- Cursor writes: agent/cursor
- both append: coordination/messages
- main is protected/read-only during normal agent work

Mandatory protocol:
1. fetch/read peer branch
2. read new coordination messages
3. review peer work
4. append a NEW review/advice message
5. do own work on owned branch
6. append a NEW status message with commit SHA

The local Cooperation module monitors branch heads and coordination messages. It must not copy large chat transcripts between UIs.

## Product rule

The three modules share browser/session infrastructure but have independent state and controls. The desktop Accessibility implementation is archived and receives no new feature work until these three web modules are stable.
