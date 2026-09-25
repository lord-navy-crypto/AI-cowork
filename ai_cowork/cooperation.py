from __future__ import annotations

import subprocess
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable


@dataclass(frozen=True)
class CoordinationSnapshot:
    chatgpt_head: str
    cursor_head: str
    coordination_head: str
    latest_message: str


class GitCoordinationMonitor:
    """Read-only GitHub coordination monitor using the repository's existing git remote."""

    def __init__(
        self,
        repo_path: str | Path = ".",
        poll_seconds: float = 10.0,
        on_status: Callable[[str], None] | None = None,
    ) -> None:
        self.repo_path = Path(repo_path).resolve()
        self.poll_seconds = max(3.0, float(poll_seconds))
        self.on_status = on_status or (lambda _: None)
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._last: CoordinationSnapshot | None = None

    @property
    def running(self) -> bool:
        return bool(self._thread and self._thread.is_alive())

    def start(self) -> bool:
        if self.running:
            return False
        self._stop.clear()
        self._thread = threading.Thread(
            target=self._run,
            name="ai-cowork-coordination-monitor",
            daemon=True,
        )
        self._thread.start()
        return True

    def stop(self) -> None:
        self._stop.set()

    def _git(self, *args: str, timeout: float = 30.0) -> str:
        result = subprocess.run(
            ["git", "-C", str(self.repo_path), *args],
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
        if result.returncode != 0:
            raise RuntimeError(result.stderr.strip() or "git command failed")
        return result.stdout.strip()

    def snapshot(self) -> CoordinationSnapshot:
        self._git(
            "fetch",
            "--quiet",
            "origin",
            "coordination",
            "agent/chatgpt",
            "agent/cursor",
        )
        chatgpt = self._git("rev-parse", "--short=12", "origin/agent/chatgpt")
        cursor = self._git("rev-parse", "--short=12", "origin/agent/cursor")
        coord = self._git("rev-parse", "--short=12", "origin/coordination")

        names = self._git(
            "ls-tree",
            "-r",
            "--name-only",
            "origin/coordination",
            "messages",
        ).splitlines()
        markdown = sorted(name for name in names if name.endswith(".md") and not name.endswith("README.md"))
        latest = markdown[-1] if markdown else "(no coordination messages yet)"
        return CoordinationSnapshot(chatgpt, cursor, coord, latest)

    def _describe_change(
        self,
        previous: CoordinationSnapshot | None,
        current: CoordinationSnapshot,
    ) -> str:
        if previous is None:
            return (
                "Cooperation connected — "
                f"ChatGPT {current.chatgpt_head}, Cursor {current.cursor_head}, "
                f"latest {current.latest_message}."
            )

        changed: list[str] = []
        if current.chatgpt_head != previous.chatgpt_head:
            changed.append("ChatGPT branch updated")
        if current.cursor_head != previous.cursor_head:
            changed.append("Cursor branch updated")
        if current.coordination_head != previous.coordination_head:
            changed.append(f"coordination updated ({current.latest_message})")
        if not changed:
            return ""
        return "Cooperation: " + "; ".join(changed) + "."

    def _run(self) -> None:
        try:
            while not self._stop.is_set():
                try:
                    current = self.snapshot()
                    message = self._describe_change(self._last, current)
                    self._last = current
                    if message:
                        self.on_status(message)
                except Exception as exc:
                    self.on_status(f"Cooperation monitor error: {exc}")
                self._stop.wait(self.poll_seconds)
        finally:
            self._thread = None


def chatgpt_peer_review_instruction() -> str:
    return (
        "Before continuing your own work: read the latest commits on agent/cursor "
        "and all new files on coordination/messages. Write a NEW append-only "
        "review/advice message on coordination. Then continue only on agent/chatgpt. "
        "Never write main."
    )


def cursor_peer_review_instruction() -> str:
    return (
        "Before continuing your own work: read the latest commits on agent/chatgpt "
        "and all new files on coordination/messages. Write a NEW append-only "
        "review/advice message on coordination. Then continue only on agent/cursor. "
        "Never write main."
    )
