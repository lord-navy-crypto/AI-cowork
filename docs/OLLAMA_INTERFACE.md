# Ollama Local AI Interface — v0.2 Alpha

## Purpose

This branch adds a **local AI strategy-provider interface** without changing the proven v0.1 swarm movement behavior.

The important boundary is:

~~~text
Ollama / local model
        ↓
high-level strategy decision
        ↓
SwarmStrategyProvider
        ↓
future strategy adapter
        ↓
existing deterministic swarm engine
~~~

v0.2 **does not yet apply AI decisions to zombie movement**. The first goal is to verify that Minecraft can discover a local Ollama server, list installed models, select a user-chosen model, and receive a valid structured strategy decision without blocking the Minecraft server thread.

## Local-only design

Default endpoint:

~~~text
http://127.0.0.1:11434
~~~

The v0.2 client rejects non-loopback hosts. Accepted hosts are:

- 127.0.0.1
- localhost
- ::1

This keeps the first AI interface explicitly local.

## Ollama API usage

The interface uses the native Ollama API:

~~~text
GET  /api/tags
POST /api/chat
~~~

Chat requests use:

~~~text
stream = false
temperature = 0
format = JSON schema
~~~

The schema constrains the model to one high-level mode:

~~~text
BASELINE
ENCIRCLE
CONCENTRATE
REGROUP
SEARCH
~~~

and three bounded multipliers:

~~~text
formationRadiusMultiplier  0.5 .. 2.0
separationMultiplier       0.5 .. 2.0
cohesionMultiplier         0.5 .. 2.0
~~~

These values are **not applied to gameplay in v0.2-alpha.1**. They are printed only so the interface can be tested safely and independently.

## Configuration

Server config keys under externalAi:

~~~text
enabled=false
ollamaBaseUrl=http://127.0.0.1:11434
ollamaModel=
ollamaTimeoutMs=3000
ollamaKeepAlive=5m
~~~

Users choose their own local model.

## Commands

Check the local server:

~~~text
/swarmmobs ai status
~~~

List models reported by local Ollama:

~~~text
/swarmmobs ai models
~~~

Select a model:

~~~text
/swarmmobs ai model <name>
~~~

Example:

~~~text
/swarmmobs ai model qwen3:8b
~~~

Send one structured interface test:

~~~text
/swarmmobs ai test
~~~

Enable or disable the future high-level provider switch:

~~~text
/swarmmobs ai on
/swarmmobs ai off
~~~

The on/off switch is intentionally not wired to zombie behavior yet.

## Threading

All Ollama HTTP operations use Java 21 asynchronous HttpClient calls.

The Minecraft server thread never waits synchronously for inference.

Command results are scheduled back onto the Minecraft server executor before writing chat output.

## Failure behavior

The architecture includes a deterministic fallback provider.

Future gameplay integration should call:

~~~text
SwarmStrategyService.decideWithFallback(...)
~~~

If Ollama is disabled, unavailable, times out, or returns invalid structured output, the service returns a neutral deterministic BASELINE decision.

The existing swarm therefore remains functional without Ollama.

## Provider interface

~~~text
SwarmStrategyProvider
├── DeterministicStrategyProvider
└── OllamaStrategyProvider
~~~

Future providers can implement the same interface without changing the swarm control code.
