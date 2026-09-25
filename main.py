from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

import yaml

from ai_cowork.agents import BrowserAgent, DesktopAgent
from ai_cowork.controller import Controller
from ai_cowork.events import EventLog
from ai_cowork.macos import (
    accessibility_trusted,
    find_pid,
    formatted_tree,
    system_events_probe,
    accessibility_debug,
    meaningful_accessibility_dump,
    webarea_text_dump,
)
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
        return cls(
            name=key,
            app_name=item["app_name"],
            poll_interval=float(cfg.get("poll_interval_seconds", 1)),
            stable_seconds=float(cfg.get("stable_output_seconds", 4)),
            enter_to_send=bool(item.get("enter_to_send", True)),
            events=events,
            read_strategy=item.get("read_strategy", "ax_tree"),
        )

    return desktop("chatgpt"), desktop("claude"), desktop("deepseek"), events


def doctor(cfg: dict) -> int:
    print("AI-cowork doctor")
    print("================")
    print(f"macOS: {'yes' if sys.platform == 'darwin' else 'NO'}")
    print(f"osascript: {shutil.which('osascript') or 'MISSING'}")
    print(f"pbcopy: {shutil.which('pbcopy') or 'MISSING'}")
    print(f"Accessibility trusted: {accessibility_trusted()}")

    ok = sys.platform == "darwin" and accessibility_trusted()
    for key in ("chatgpt", "claude", "deepseek"):
        item = cfg["agents"][key]
        pid = find_pid(item["app_name"])
        print(f"{key:10} app={item['app_name']!r} pid={pid or 'not running'}")
        if key in ("chatgpt", "claude") and pid is None:
            ok = False

    print()
    print("If Accessibility trusted is False:")
    print("System Settings → Privacy & Security → Accessibility")
    print("Enable your terminal app, then quit and reopen Terminal.")
    return 0 if ok else 2


def main() -> int:
    parser = argparse.ArgumentParser(prog="ai-cowork")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("doctor")

    inspect_p = sub.add_parser("inspect")
    inspect_p.add_argument("agent", choices=["chatgpt", "claude", "deepseek"])

    probe_p = sub.add_parser("probe")
    probe_p.add_argument("agent", choices=["chatgpt", "claude", "deepseek"])

    debug_p = sub.add_parser("debug-ax")
    debug_p.add_argument("agent", choices=["chatgpt", "claude", "deepseek"])

    deep_p = sub.add_parser("deep-ax")
    deep_p.add_argument("agent", choices=["chatgpt", "claude", "deepseek"])

    text_p = sub.add_parser("web-text")
    text_p.add_argument("agent", choices=["chatgpt", "claude", "deepseek"])

    snap_p = sub.add_parser("snapshot")
    snap_p.add_argument("agent", choices=["chatgpt", "claude", "deepseek"])

    send_p = sub.add_parser("send")
    send_p.add_argument("agent", choices=["chatgpt", "claude", "deepseek"])
    send_p.add_argument("message")

    sar_p = sub.add_parser("send-and-read")
    sar_p.add_argument("agent", choices=["chatgpt", "claude", "deepseek"])
    sar_p.add_argument("message")
    sar_p.add_argument("--timeout", type=float, default=300)

    relay_p = sub.add_parser("relay")
    relay_p.add_argument("message")
    relay_p.add_argument("--timeout", type=float, default=300)

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

    if args.command == "probe":
        item = cfg["agents"][args.agent]
        print(f"agent={args.agent}")
        print(f"app={item['app_name']}")
        print(f"pid={find_pid(item['app_name'])}")
        print(f"accessibility_trusted={accessibility_trusted()}")
        probe = system_events_probe(item["app_name"])
        print(f"system_events_ok={probe['ok']}")
        print(probe["output"])
        return 0

    if args.command == "debug-ax":
        print(accessibility_debug(mapping[args.agent].app_name))
        return 0

    if args.command == "deep-ax":
        print(meaningful_accessibility_dump(mapping[args.agent].app_name))
        return 0

    if args.command == "web-text":
        print(webarea_text_dump(mapping[args.agent].app_name))
        return 0

    if args.command == "snapshot":
        print(mapping[args.agent].read_snapshot())
        return 0

    if args.command == "send":
        mapping[args.agent].send(args.message)
        print("sent")
        return 0

    if args.command == "send-and-read":
        print(mapping[args.agent].send_and_read(args.message, timeout=args.timeout))
        return 0

    if args.command == "relay":
        print("[relay] GPT round 1: sending + waiting...", flush=True)
        gpt_reply = gpt.send_and_read(args.message, timeout=args.timeout)
        print("[relay] GPT round 1: complete", flush=True)
        print("===== GPT ROUND 1 =====")
        print(gpt_reply)

        claude_prompt = (
            "You are collaborating with ChatGPT on the same task. "
            "Independently review the following ChatGPT response. "
            "Identify errors, omissions, disagreements, and concrete improvements.\n\n"
            "CHATGPT RESPONSE:\n" + gpt_reply
        )
        print("[relay] Claude review: sending + waiting...", flush=True)
        claude_reply = claude.send_and_read(claude_prompt, timeout=args.timeout)
        print("[relay] Claude review: complete", flush=True)
        print("\n===== CLAUDE REVIEW =====")
        print(claude_reply)

        gpt_followup = (
            "Claude reviewed your previous response. Evaluate the review independently, "
            "accept only well-supported suggestions, correct any mistakes, and produce the "
            "next improved result.\n\nCLAUDE REVIEW:\n" + claude_reply
        )
        print("[relay] GPT round 2: sending + waiting...", flush=True)
        final_reply = gpt.send_and_read(gpt_followup, timeout=args.timeout)
        print("[relay] GPT round 2: complete", flush=True)
        print("\n===== GPT ROUND 2 =====")
        print(final_reply)
        return 0

    if args.command == "run":
        if args.dry_run:
            print("Dry-run configuration OK.")
            print(f"checkpoint_seconds={cfg['checkpoint_seconds']}")
            print(f"max_rounds={cfg['max_rounds']}")
            return 0

        store = RuntimeStore()
        store.load()
        Controller(
            gpt=gpt,
            claude=claude,
            deepseek=deepseek,
            checkpoint_seconds=int(cfg["checkpoint_seconds"]),
            max_rounds=int(cfg["max_rounds"]),
            store=store,
            events=events,
        ).run()
        return 0

    return 1


if __name__ == "__main__":
    raise SystemExit(main())
