from __future__ import annotations

import argparse
import shutil
import time
from pathlib import Path

from ai_cowork.cooperation import (
    GitCoordinationMonitor,
    ProtocolDraftStore,
    ProtocolGate,
    draft_required_coordination_message,
    protocol_next_action,
)
from ai_cowork.runtime_state import RuntimeStateStore, classify_status_message
from ai_cowork.web_runtime import (
    SettingsStore,
    WebAutomationRuntime,
    validate_chatgpt_url,
    validate_cursor_url,
)


def doctor() -> int:
    print("AI-cowork web doctor")
    print("====================")

    settings = SettingsStore().load()
    runtime_state = RuntimeStateStore().load()
    web_needed = (
        settings.chatgpt_supervisor_enabled
        or settings.cursor_supervisor_enabled
    )

    ok = True
    if web_needed:
        try:
            import playwright  # noqa: F401
            print("Playwright Python package: yes")
        except Exception as exc:
            print(f"Playwright Python package: NO ({exc})")
            ok = False

        try:
            from playwright.sync_api import sync_playwright

            p = sync_playwright().start()
            executable = Path(p.chromium.executable_path)
            installed = executable.exists()
            print(f"Chromium installed: {'yes' if installed else 'NO'}")
            print(f"Chromium path: {executable}")
            p.stop()
            if not installed:
                ok = False
        except Exception as exc:
            print(f"Chromium installed: NO ({exc})")
            ok = False
    else:
        print("Playwright/Chromium: not required (web supervisors disabled)")

    git_path = shutil.which("git")
    if settings.cooperation_enabled:
        print(f"Git: {git_path or 'MISSING'}")
        if not git_path:
            ok = False

    chat_ok = validate_chatgpt_url(settings.chatgpt_url)
    cursor_ok = validate_cursor_url(settings.cursor_url)
    print(
        "ChatGPT Supervisor: "
        + (
            "ready"
            if settings.chatgpt_supervisor_enabled and chat_ok
            else "disabled"
            if not settings.chatgpt_supervisor_enabled
            else "URL required"
        )
    )
    print(
        "Cursor Supervisor: "
        + (
            "ready"
            if settings.cursor_supervisor_enabled and cursor_ok
            else "disabled"
            if not settings.cursor_supervisor_enabled
            else "URL required"
        )
    )
    print(
        "Cooperation: "
        + ("enabled" if settings.cooperation_enabled else "disabled")
    )
    print(
        "Cursor verified auto-continue: "
        + ("enabled" if settings.cursor_auto_continue else "disabled")
    )
    if runtime_state.updated_at:
        print(f"Last runtime update: {runtime_state.updated_at}")
        print(f"Last ChatGPT state: {runtime_state.chatgpt}")
        print(f"Last Cursor state: {runtime_state.cursor}")
        print(f"Last Cooperation state: {runtime_state.cooperation}")

    if web_needed and not ok:
        print()
        print("For web supervisors:")
        print("  pip install -r requirements.txt")
        print("  python -m playwright install chromium")
    return 0 if ok else 2


def show_protocol() -> int:
    monitor = GitCoordinationMonitor(".")
    try:
        snapshot = monitor.snapshot()
    except Exception as exc:
        print(f"Protocol check failed: {exc}")
        return 2

    print("AI-cowork cooperation protocol")
    print("=============================")
    for label, state in (
        ("ChatGPT", snapshot.chatgpt_protocol),
        ("Cursor", snapshot.cursor_protocol),
    ):
        if state is None:
            print(f"{label}: UNKNOWN")
            continue
        print(f"{label}: {state.state.value}")
        print(f"  {state.detail}")
        print(f"  own={state.own_head} peer={state.peer_head}")
        print(f"  review={state.latest_review}")
        print(f"  status={state.latest_status}")
        print(f"  next={protocol_next_action(state)}")
    print(f"Latest coordination message: {snapshot.latest_message}")
    return 0


def draft_protocol_message(agent: str, summary: str) -> int:
    monitor = GitCoordinationMonitor(".")
    try:
        snapshot = monitor.snapshot()
    except Exception as exc:
        print(f"Protocol check failed: {exc}")
        return 2

    state = (
        snapshot.chatgpt_protocol
        if agent == "chatgpt"
        else snapshot.cursor_protocol
    )
    if state is None:
        print(f"No protocol state available for {agent}.")
        return 2

    try:
        path, body = draft_required_coordination_message(
            state,
            summary=summary,
        )
    except ValueError as exc:
        print(str(exc))
        return 2

    print(path)
    print()
    print(body)
    return 0


def run_supervisor() -> int:
    settings = SettingsStore().load()
    if (
        settings.chatgpt_supervisor_enabled
        and not validate_chatgpt_url(settings.chatgpt_url)
    ):
        print("ChatGPT Supervisor is enabled but no valid ChatGPT URL is saved.")
        print("Run python main.py and save a ChatGPT URL, or disable that module.")
        return 2
    if (
        settings.cursor_supervisor_enabled
        and not validate_cursor_url(settings.cursor_url)
    ):
        print("Cursor Supervisor is enabled but no valid Cursor URL is saved.")
        print("Run python main.py and save a Cursor URL, or disable that module.")
        return 2
    if not (
        settings.chatgpt_supervisor_enabled
        or settings.cursor_supervisor_enabled
        or settings.cooperation_enabled
    ):
        print("No module is enabled.")
        return 2

    state_store = RuntimeStateStore()
    protocol_gate = ProtocolGate()
    protocol_drafts = ProtocolDraftStore()
    if settings.cooperation_enabled:
        protocol_gate.enable()

    runtime = None
    web_enabled = (
        settings.chatgpt_supervisor_enabled
        or settings.cursor_supervisor_enabled
    )
    if web_enabled:
        runtime = WebAutomationRuntime(
            settings,
            on_status=lambda message: (
                state_store.update(classify_status_message(message), message),
                print(f"[web] {message}", flush=True),
            )[-1],
            protocol_gate=protocol_gate,
        )
        runtime.start()

    cooperation = None
    if settings.cooperation_enabled:
        def handle_snapshot(snapshot):
            protocol_gate.update(snapshot)
            protocol_drafts.write_snapshot(snapshot)

        cooperation = GitCoordinationMonitor(
            ".",
            poll_seconds=10.0,
            on_status=lambda message: (
                state_store.update("cooperation", message),
                print(f"[cooperation] {message}", flush=True),
            )[-1],
            on_snapshot=handle_snapshot,
        )
        cooperation.start()
    try:
        while (
            (runtime is not None and runtime.running)
            or (cooperation is not None and cooperation.running)
        ):
            time.sleep(0.5)
    except KeyboardInterrupt:
        if runtime is not None:
            runtime.stop()
    finally:
        if cooperation and cooperation.running:
            cooperation.stop()
        protocol_gate.disable()
        if runtime is not None:
            while runtime.running:
                time.sleep(0.1)
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        prog="ai-cowork",
        description="Background web supervisor for ChatGPT + Cursor.",
    )
    sub = parser.add_subparsers(dest="command")
    sub.add_parser("gui", help="Open the URL control app.")
    sub.add_parser("supervisor", help="Run the saved ChatGPT Web supervisor.")
    sub.add_parser("doctor", help="Check Playwright and browser installation.")
    sub.add_parser("protocol", help="Show current cooperation protocol gates.")
    draft = sub.add_parser(
        "draft-message",
        help="Draft the protocol-required coordination message for an agent.",
    )
    draft.add_argument("agent", choices=("chatgpt", "cursor"))
    draft.add_argument(
        "--summary",
        required=True,
        help="Short review/status summary to place in the draft.",
    )

    args = parser.parse_args()

    if args.command == "doctor":
        return doctor()
    if args.command == "supervisor":
        return run_supervisor()
    if args.command == "protocol":
        return show_protocol()
    if args.command == "draft-message":
        return draft_protocol_message(args.agent, args.summary)

    from ai_cowork.gui import run_gui

    run_gui()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
