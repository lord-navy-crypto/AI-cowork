# AI-cowork

AI-cowork is currently focused on one reliable macOS feature:

**ChatGPT Work Supervisor** — watch the ChatGPT desktop app, wait while it is generating, and send a short continue instruction after the current work turn is genuinely finished.

Previous multi-agent experiments are preserved on:

`future/multi-agent-orchestration`

They are intentionally not part of the current `main` product.

## What the supervisor does

1. Watches the ChatGPT desktop app through macOS Accessibility.
2. Does not interrupt while ChatGPT is visibly generating.
3. Waits for a short confirmed idle period.
4. Sends the configured continue prompt.
5. Waits for the next complete response.
6. Repeats until stopped.
7. If a clear conversation/context-limit message is detected, pauses and shows a macOS notification.

## Window

The native macOS window contains three controls:

- **Start / Stop ChatGPT Supervisor** — active.
- **Multi-agent collaboration** — Under development.
- **Repository automation** — Under development.

The latter two are placeholders only. Their previous implementation work is preserved on the future branch.

## Install

```bash
git clone https://github.com/lord-navy-crypto/AI-cowork.git
cd AI-cowork

python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp config.example.yaml config.yaml
```

Enable Accessibility permission for the terminal/application running AI-cowork:

**System Settings → Privacy & Security → Accessibility**

## Run

Open the native window:

```bash
python main.py
```

or explicitly:

```bash
python main.py gui
```

Check permissions and whether ChatGPT is running:

```bash
python main.py doctor
```

Run without the GUI:

```bash
python main.py supervisor
```

Print the current ChatGPT snapshot:

```bash
python main.py snapshot
```

## Configuration

`config.yaml` can change the continue text and the idle confirmation delay.

The supervisor defaults to:

```text
Continue doing the current task. Keep working from where you stopped.
Do not restart or summarize unless necessary; continue the actual work.
```

## Design rule

The supervisor is intentionally mechanical. It does not decide what project work should be done. It only keeps an already-running ChatGPT work session moving when the user does not want to watch the window continuously.


## Background operation

AI-cowork is designed to stay out of the way while you use the Mac for other work.

The supervisor now uses a background-first send path:

1. **Background Accessibility transport** — writes directly to the ChatGPT composer and presses Send through Accessibility. This does not activate ChatGPT and does not touch the clipboard.
2. **Foreground fallback** — used only when the current ChatGPT build does not expose a reliable writable composer. This fallback may briefly bring ChatGPT forward because macOS keyboard events target the frontmost application.

To require strict background-only behavior, set:

```yaml
chatgpt:
  background_preferred: true
  allow_foreground_fallback: false
```

Passive reading of the ChatGPT WebArea and generation-state checks do not require bringing ChatGPT to the foreground.
