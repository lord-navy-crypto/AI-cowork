from __future__ import annotations

import argparse
import time
from pathlib import Path

from ai_cowork.cooperation import GitCoordinationMonitor
from ai_cowork.web_runtime import (
    SettingsStore,
    WebAutomationRuntime,
    validate_chatgpt_url,
    validate_cursor_url,
)


def doctor() -> int:
    print("AI-cowork web doctor")
    print("====================")

    ok = True
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

    settings = SettingsStore().load()
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

    if not ok:
        print()
        print("Run: pip install -r requirements.txt")
        print("Then: python -m playwright install chromium")
    return 0 if ok else 2


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

    runtime = None
    web_enabled = (
        settings.chatgpt_supervisor_enabled
        or settings.cursor_supervisor_enabled
    )
    if web_enabled:
        runtime = WebAutomationRuntime(
            settings,
            on_status=lambda message: print(f"[web] {message}", flush=True),
        )
        runtime.start()

    cooperation = None
    if settings.cooperation_enabled:
        cooperation = GitCoordinationMonitor(
            ".",
            poll_seconds=10.0,
            on_status=lambda message: print(f"[cooperation] {message}", flush=True),
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

    args = parser.parse_args()

    if args.command == "doctor":
        return doctor()
    if args.command == "supervisor":
        return run_supervisor()

    from ai_cowork.gui import run_gui

    run_gui()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
