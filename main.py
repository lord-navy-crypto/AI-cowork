from __future__ import annotations

import argparse
import shutil
import sys
import time
from pathlib import Path

import yaml

from ai_cowork.agents import DesktopAgent
from ai_cowork.events import EventLog
from ai_cowork.macos import accessibility_trusted, find_pid
from ai_cowork.supervisor import ChatGPTSupervisor, SupervisorConfig


def load_config(path: str = "config.yaml") -> dict:
    p = Path(path)
    if not p.exists():
        p = Path("config.example.yaml")
    return yaml.safe_load(p.read_text(encoding="utf-8"))


def build_chatgpt(cfg: dict) -> DesktopAgent:
    item = cfg["chatgpt"]
    return DesktopAgent(
        name="chatgpt",
        app_name=item.get("app_name", "ChatGPT"),
        poll_interval=float(cfg.get("poll_interval_seconds", 1)),
        stable_seconds=float(cfg.get("stable_output_seconds", 4)),
        enter_to_send=True,
        events=EventLog(),
        read_strategy=item.get("read_strategy", "webarea"),
        background_preferred=bool(item.get("background_preferred", True)),
        allow_foreground_fallback=bool(item.get("allow_foreground_fallback", True)),
    )


def build_supervisor(cfg: dict) -> ChatGPTSupervisor:
    supervisor_cfg = cfg.get("supervisor", {})
    return ChatGPTSupervisor(
        build_chatgpt(cfg),
        SupervisorConfig(
            continue_prompt=supervisor_cfg.get(
                "continue_prompt",
                "Continue doing the current task. Keep working from where you stopped.",
            ),
            idle_confirm_seconds=float(
                supervisor_cfg.get("idle_confirm_seconds", 3)
            ),
            poll_interval_seconds=float(
                cfg.get("poll_interval_seconds", 1)
            ),
        ),
    )


def doctor(cfg: dict) -> int:
    app_name = cfg["chatgpt"].get("app_name", "ChatGPT")
    pid = find_pid(app_name)
    print("AI-cowork supervisor doctor")
    print("==========================")
    print(f"macOS: {'yes' if sys.platform == 'darwin' else 'NO'}")
    print(f"osascript: {shutil.which('osascript') or 'MISSING'}")
    print(f"pbcopy: {shutil.which('pbcopy') or 'MISSING'}")
    print(f"Accessibility trusted: {accessibility_trusted()}")
    print(f"ChatGPT app: {app_name!r} pid={pid or 'not running'}")
    ok = (
        sys.platform == "darwin"
        and accessibility_trusted()
        and pid is not None
    )
    return 0 if ok else 2


def run_headless(supervisor: ChatGPTSupervisor) -> int:
    supervisor.on_status = lambda message: print(
        f"[supervisor] {message}", flush=True
    )
    supervisor.start()
    try:
        while supervisor.running:
            time.sleep(0.5)
    except KeyboardInterrupt:
        supervisor.stop()
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        prog="ai-cowork",
        description="Keep ChatGPT desktop work moving automatically.",
    )
    sub = parser.add_subparsers(dest="command")
    sub.add_parser("gui", help="Open the native supervisor window.")
    sub.add_parser("supervisor", help="Run the supervisor in the terminal.")
    sub.add_parser("doctor", help="Check ChatGPT + macOS permissions.")
    sub.add_parser("snapshot", help="Print the current ChatGPT text snapshot.")

    args = parser.parse_args()
    cfg = load_config()

    if args.command == "doctor":
        return doctor(cfg)

    if args.command == "snapshot":
        print(build_chatgpt(cfg).read_snapshot())
        return 0

    supervisor = build_supervisor(cfg)

    if args.command == "supervisor":
        return run_headless(supervisor)

    # Import Cocoa UI only when the GUI is actually requested. This keeps
    # doctor/supervisor usable even if a future GUI-specific compatibility
    # issue appears in PyObjC.
    from ai_cowork.gui import run_gui

    run_gui(supervisor)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
