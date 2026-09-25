from __future__ import annotations

import argparse
import shutil
import sys
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeoutError, wait
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
    cancel_generation,
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

    return desktop("chatgpt"), desktop("cursor"), desktop("deepseek"), events


def doctor(cfg: dict) -> int:
    print("AI-cowork doctor")
    print("================")
    print(f"macOS: {'yes' if sys.platform == 'darwin' else 'NO'}")
    print(f"osascript: {shutil.which('osascript') or 'MISSING'}")
    print(f"pbcopy: {shutil.which('pbcopy') or 'MISSING'}")
    print(f"Accessibility trusted: {accessibility_trusted()}")

    ok = sys.platform == "darwin" and accessibility_trusted()
    for key in ("chatgpt", "cursor", "deepseek"):
        item = cfg["agents"][key]
        pid = find_pid(item["app_name"])
        print(f"{key:10} app={item['app_name']!r} pid={pid or 'not running'}")
        if key in ("chatgpt", "cursor") and pid is None:
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
    inspect_p.add_argument("agent", choices=["chatgpt", "cursor", "deepseek"])

    probe_p = sub.add_parser("probe")
    probe_p.add_argument("agent", choices=["chatgpt", "cursor", "deepseek"])

    debug_p = sub.add_parser("debug-ax")
    debug_p.add_argument("agent", choices=["chatgpt", "cursor", "deepseek"])

    deep_p = sub.add_parser("deep-ax")
    deep_p.add_argument("agent", choices=["chatgpt", "cursor", "deepseek"])

    text_p = sub.add_parser("web-text")
    text_p.add_argument("agent", choices=["chatgpt", "cursor", "deepseek"])

    snap_p = sub.add_parser("snapshot")
    snap_p.add_argument("agent", choices=["chatgpt", "cursor", "deepseek"])

    send_p = sub.add_parser("send")
    send_p.add_argument("agent", choices=["chatgpt", "cursor", "deepseek"])
    send_p.add_argument("message")

    sar_p = sub.add_parser("send-and-read")
    sar_p.add_argument("agent", choices=["chatgpt", "cursor", "deepseek"])
    sar_p.add_argument("message")
    sar_p.add_argument("--timeout", type=float, default=None,
                       help="Optional hard timeout in seconds; default waits indefinitely.")

    relay_p = sub.add_parser("relay")
    relay_p.add_argument("message")
    relay_p.add_argument("--timeout", type=float, default=None,
                         help="Optional hard timeout in seconds; default waits indefinitely.")

    ping_p = sub.add_parser("ping-pong")
    ping_p.add_argument("message")
    ping_p.add_argument("--rounds", type=int, default=0,
                        help="Number of barrier rounds; 0 means continue until Ctrl+C.")
    ping_p.add_argument("--stuck-minutes", type=float, default=0,
                        help="Optional watchdog; 0 disables forced cancellation.")

    run_p = sub.add_parser("run")
    run_p.add_argument("--dry-run", action="store_true")
    run_p.add_argument("--preset", choices=["redstone"])
    run_p.add_argument("--gpt-objective")
    run_p.add_argument("--cursor-objective")
    run_p.add_argument("--with-deepseek", action="store_true")

    args = parser.parse_args()
    cfg = load_config()
    gpt, cursor, deepseek, events = build_agents(cfg)
    mapping = {"chatgpt": gpt, "cursor": cursor, "deepseek": deepseek}

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

    if args.command == "ping-pong":
        prompts = {
            "chatgpt": args.message,
            "cursor": args.message,
        }
        round_index = 0

        print(
            "[ping-pong] barrier mode: both AIs must finish before any handoff",
            flush=True,
        )

        with ThreadPoolExecutor(max_workers=2) as pool:
            while args.rounds == 0 or round_index < args.rounds:
                round_index += 1
                print(f"[ping-pong] round {round_index}: sending both tasks...", flush=True)

                # Start both work turns before waiting on either result.
                gpt.send(prompts["chatgpt"])
                cursor.send(prompts["cursor"])

                print(
                    f"[ping-pong] round {round_index}: both working; waiting for BOTH to complete...",
                    flush=True,
                )

                futures = {
                    "chatgpt": pool.submit(gpt.wait_until_stable, None),
                    "cursor": pool.submit(cursor.wait_until_stable, None),
                }

                if args.stuck_minutes > 0:
                    watchdog_seconds = args.stuck_minutes * 60.0
                    done, not_done = wait(futures.values(), timeout=watchdog_seconds)
                    if not_done:
                        stuck_names = [
                            name for name, future in futures.items() if future in not_done
                        ]
                        print(
                            "[ping-pong] watchdog: cancelling stuck generation(s): "
                            + ", ".join(stuck_names),
                            flush=True,
                        )
                        for name in stuck_names:
                            cancel_generation(mapping[name].app_name)

                replies = {}
                for name in ("chatgpt", "cursor"):
                    try:
                        # With no watchdog this is an unlimited barrier wait.
                        # After watchdog cancellation, allow the UI time to settle
                        # and return its partial/final visible output.
                        settle_timeout = 20.0 if args.stuck_minutes > 0 else None
                        replies[name] = futures[name].result(timeout=settle_timeout)
                    except FutureTimeoutError:
                        raise RuntimeError(
                            f"{name} did not settle after watchdog cancellation; "
                            "manual intervention is required"
                        )
                    print(
                        f"[ping-pong] round {round_index}: {name} READY; "
                        "handoff blocked until the other side is also READY",
                        flush=True,
                    )

                print(
                    f"[ping-pong] round {round_index}: BARRIER OPEN — both complete",
                    flush=True,
                )
                print("===== CHATGPT WORK REPORT =====")
                print(replies["chatgpt"])
                print("===== CURSOR WORK REPORT =====")
                print(replies["cursor"])

                if args.rounds != 0 and round_index >= args.rounds:
                    break

                prompts["chatgpt"] = (
                    "DEVELOPER HANDOFF FROM CURSOR\n\n"
                    "Cursor has completed its work round. Continue engineering work using "
                    "this report as context. Inspect the repository/branch before editing. "
                    "Do real implementation and testing. Do not merge main or force push. "
                    "When genuinely complete, reply with a concise WORK REPORT containing "
                    "branch, completed work, files changed, tests, problems, commit, and "
                    "the next useful step.\n\n"
                    "CURSOR REPORT:\n"
                    + replies["cursor"]
                )
                prompts["cursor"] = (
                    "DEVELOPER HANDOFF FROM CHATGPT\n\n"
                    "ChatGPT has completed its work round. Continue engineering work using "
                    "this report as context. Inspect the repository/branch before editing. "
                    "Do real implementation and testing. Do not merge main or force push. "
                    "When genuinely complete, reply with a concise WORK REPORT containing "
                    "branch, completed work, files changed, tests, problems, commit, and "
                    "the next useful step.\n\n"
                    "CHATGPT REPORT:\n"
                    + replies["chatgpt"]
                )

        return 0

    if args.command == "relay":
        print("[relay] GPT round 1: sending + waiting...", flush=True)
        gpt_reply = gpt.send_and_read(args.message, timeout=args.timeout)
        print("[relay] GPT round 1: complete", flush=True)
        print("===== GPT ROUND 1 =====")
        print(gpt_reply)

        cursor_prompt = (
            "You are collaborating with ChatGPT on the same task. "
            "Independently review the following ChatGPT response. "
            "Identify errors, omissions, disagreements, and concrete improvements.\n\n"
            "CHATGPT RESPONSE:\n" + gpt_reply
        )
        print("[relay] Cursor review: sending + waiting...", flush=True)
        cursor_reply = cursor.send_and_read(cursor_prompt, timeout=args.timeout)
        print("[relay] Cursor review: complete", flush=True)
        print("\n===== CURSOR REVIEW =====")
        print(cursor_reply)

        gpt_followup = (
            "Cursor reviewed your previous response. Evaluate the review independently, "
            "accept only well-supported suggestions, correct any mistakes, and produce the "
            "next improved result.\n\nCURSOR REVIEW:\n" + cursor_reply
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

        if args.preset == "redstone":
            gpt_objective = args.gpt_objective or (
                "Work on lord-navy-crypto/redstones-engineering as the systems/reliability "
                "developer. Inspect the real repository first. Improve a substantive weakness "
                "in signal semantics, device-state handling, control/reliability behavior, or "
                "verification. Prefer changes that make real zero distinct from missing/stale/"
                "not-ready information and strengthen end-to-end engineering correctness. "
                "Run the relevant verification/build checks and commit the work on agent/gpt."
            )
            cursor_objective = args.cursor_objective or (
                "Work independently on lord-navy-crypto/redstones-engineering as the HMI/"
                "instrumentation developer. Inspect the real repository first. Improve a "
                "substantive weakness in player-facing diagnostics, visualization, instrument "
                "readability, commissioning workflow, or operations feedback. Keep the work "
                "architecturally compatible with the existing engineering systems, run relevant "
                "tests, and commit the work on agent/cursor."
            )
        else:
            gpt_objective = args.gpt_objective or (
                "Inspect the repository and implement a substantive systems/reliability improvement."
            )
            cursor_objective = args.cursor_objective or (
                "Inspect the repository independently and implement a substantive complementary improvement."
            )

        store = RuntimeStore()
        store.load()
        Controller(
            gpt=gpt,
            cursor=cursor,
            deepseek=deepseek if args.with_deepseek else None,
            checkpoint_seconds=int(cfg["checkpoint_seconds"]),
            max_rounds=int(cfg["max_rounds"]),
            store=store,
            events=events,
            gpt_objective=gpt_objective,
            cursor_objective=cursor_objective,
        ).run()
        return 0

    return 1


if __name__ == "__main__":
    raise SystemExit(main())
