from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

import yaml

from ai_cowork.agents import BrowserAgent, DesktopAgent
from ai_cowork.controller import Controller
from ai_cowork.events import EventLog
from ai_cowork.macos import find_pid, formatted_tree
from ai_cowork.state import RuntimeStore


def load_config(path: str = "config.yaml") -> dict:
    p = Path(path)
    if not p.exists():
        p = Path("config.example.yaml")
    return yaml.safe_load(p.read_text())


def build_agents(cfg: dict):
    events = EventLog()

    def desktop(key: str):
        item = cfg["agents"][key]
        cls = BrowserAgent if item.get("kind") == "browser" else DesktopAgent
        kwargs = dict(
            name=key,
            app_name=item["app_name"],
            poll_interval=float(cfg.get("poll_interval_seconds", 1)),
            stable_seconds=float(cfg.get("stable_output_seconds", 4)),
            enter_to_send=bool(item.get("enter_to_send", True)),
            events=events,
        )
        return cls(**kwargs)

    return desktop("chatgpt"), desktop("claude"), desktop("deepseek"), events


def doctor(cfg: dict) -> int:
    print("AI-cowork doctor")
    print("================")
    print(f"macOS: {'yes' if sys.platform == 'darwin' else 'NO'}")
    print(f"osascript: {shutil.which('osascript') or 'MISSING'}")
    print(f"pbcopy: {shutil.which('pbcopy') or 'MISSING'}")

    ok = sys.platform == "darwin"
    for key in ("chatgpt", "claude", "deepseek"):
        item = cfg["agents"][key]
        pid = find_pid(item["app_name"])
        print(f"{key:10} app={item['app_name']!r} pid={pid or 'not running'}")
        if key in ("chatgpt", "claude") and pid is None:
            ok = False

    print()
    print("If inspect/send fails, grant Terminal Accessibility permission:")
    print("System Settings → Privacy & Security → Accessibility")
    return 0 if ok else 2


def main() -> int:
    parser = argparse.ArgumentParser(prog="ai-cowork")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("doctor")

    inspect_p = sub.add_parser("inspect")
    inspect_p.add_argument("agent", choices=["chatgpt", "claude", "deepseek"])

    send_p = sub.add_parser("send")
    send_p.add_argument("agent", choices=["chatgpt", "claude", "deepseek"])
    send_p.add_argument("message")

    run_p = sub.add_parser("run")
    run_p.add_argument("--dry-run", action="store_true")

    args = parser.parse_args()
    cfg = load_config()
    gpt, claude, deepseek, events = build_agents(cfg)
    mapping = {"chatgpt": gpt, "claude": claude, "deepseek": deepseek}

    if args.command == "doctor":
        return doctor(cfg)

    if args.command == "inspect":
        print(formatted_tree(mapping[args.agent].app_name))
        return 0

    if args.command == "send":
        mapping[args.agent].send(args.message)
        print("sent")
        return 0

    if args.command == "run":
        if args.dry_run:
            print("Dry-run configuration OK.")
            print(f"checkpoint_seconds={cfg['checkpoint_seconds']}")
            print(f"max_rounds={cfg['max_rounds']}")
            return 0

        store = RuntimeStore()
        store.load()
        controller = Controller(
            gpt=gpt,
            claude=claude,
            deepseek=deepseek,
            checkpoint_seconds=int(cfg["checkpoint_seconds"]),
            max_rounds=int(cfg["max_rounds"]),
            store=store,
            events=events,
        )
        controller.run()
        return 0

    return 1


if __name__ == "__main__":
    raise SystemExit(main())
