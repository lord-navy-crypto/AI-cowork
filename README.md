# AI-cowork

A local macOS orchestrator for coordinating:

- **ChatGPT.app** — Developer A
- **Claude.app** — Developer B
- **DeepSeek (browser)** — read-only consultant / project brief

The first milestone is intentionally terminal-first. The controller uses macOS Accessibility + AppleScript/clipboard fallbacks rather than screen coordinates.

## Safety model

AI-cowork does **not** auto-merge `main`, force-push, run `git reset --hard`, or allow destructive `rm -rf` operations. Developer agents should work in isolated Git worktrees.

## macOS requirements

- macOS
- Python 3.11+
- ChatGPT desktop app
- Claude desktop app
- Chrome (or another Chromium browser) logged into DeepSeek
- Accessibility permission for Terminal (later, for the packaged app, permission for AI-cowork itself)

## Install

```bash
git clone https://github.com/lord-navy-crypto/AI-cowork.git
cd AI-cowork

python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp config.example.yaml config.yaml
```

Then enable:

**System Settings → Privacy & Security → Accessibility → Terminal**

Run the diagnostics first:

```bash
python main.py doctor
```

Inspect the accessibility trees:

```bash
python main.py inspect chatgpt
python main.py inspect claude
```

Test send + automatic response capture:

```bash
python main.py send-and-read chatgpt "Reply exactly: GPT_READY"
python main.py send-and-read claude "Reply exactly: CLAUDE_READY"
```

Test one complete collaboration relay:

```bash
python main.py relay "Analyze this task and propose the first implementation step."
```

The relay performs: ChatGPT initial answer → Claude independent review → ChatGPT revised answer.

Start a dry-run orchestration session:

```bash
python main.py run --dry-run
```

## Architecture

```text
                   AI-cowork Controller
                           |
          +----------------+----------------+
          |                |                |
      ChatGPT.app       Claude.app     DeepSeek Web
      Developer A       Developer B     Consultant
          |                |
      agent/gpt        agent/claude
          \______________/
             cross-review
```

### Agent states

`IDLE → WORKING → WAITING → REPORTING → REVIEWING → BLOCKED / ERROR / DONE`

### Checkpoint protocol

By default the scheduler requests a checkpoint every 10 minutes. A checkpoint asks each developer to safely finish its current atomic step and report:

1. completed work
2. changed files
3. tests
4. failures/blockers
5. branch and commit
6. questions for the other developer
7. next actions

The controller then routes reports for cross-review and can send a consolidated brief to DeepSeek.

## Current status

This repository contains the **MVP foundation**. The desktop adapters already include:

- app discovery
- app activation
- clipboard paste + Enter
- Accessibility-tree text extraction
- stable-output polling
- event logging
- state persistence
- safety command screening

The exact Accessibility hierarchy of ChatGPT/Claude can vary by app version. Run `inspect` on your Mac and, if necessary, tune the selectors in `config.yaml`.
