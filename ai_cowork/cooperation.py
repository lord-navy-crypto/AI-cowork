from __future__ import annotations

import subprocess
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum
from pathlib import Path
from typing import Callable


class ProtocolState(str, Enum):
    REVIEW_REQUIRED = "REVIEW_REQUIRED"
    OWN_WORK_ALLOWED = "OWN_WORK_ALLOWED"
    STATUS_REQUIRED = "STATUS_REQUIRED"
    READY = "READY"


@dataclass(frozen=True)
class AgentProtocolState:
    agent: str
    state: ProtocolState
    detail: str
    peer_head: str
    own_head: str
    latest_review: str
    latest_status: str


def evaluate_protocol_state(
    agent: str,
    own_head: str,
    peer_head: str,
    own_ts: int,
    peer_ts: int,
    review_ts: int,
    status_ts: int,
    review_path: str = "",
    status_path: str = "",
) -> AgentProtocolState:
    if peer_ts > review_ts:
        return AgentProtocolState(
            agent,
            ProtocolState.REVIEW_REQUIRED,
            f"{agent} must review the peer branch before doing more own work.",
            peer_head,
            own_head,
            review_path or "(none)",
            status_path or "(none)",
        )

    if own_ts > status_ts:
        return AgentProtocolState(
            agent,
            ProtocolState.STATUS_REQUIRED,
            f"{agent} has newer own-branch work and must append a status message.",
            peer_head,
            own_head,
            review_path or "(none)",
            status_path or "(none)",
        )

    if review_ts >= own_ts and review_ts >= peer_ts:
        return AgentProtocolState(
            agent,
            ProtocolState.OWN_WORK_ALLOWED,
            f"{agent} has reviewed the current peer state and may work on its owned branch.",
            peer_head,
            own_head,
            review_path or "(none)",
            status_path or "(none)",
        )

    return AgentProtocolState(
        agent,
        ProtocolState.READY,
        f"{agent} protocol obligations are currently satisfied.",
        peer_head,
        own_head,
        review_path or "(none)",
        status_path or "(none)",
    )


@dataclass(frozen=True)
class CoordinationSnapshot:
    chatgpt_head: str
    cursor_head: str
    coordination_head: str
    latest_message: str
    chatgpt_protocol: AgentProtocolState | None = None
    cursor_protocol: AgentProtocolState | None = None


class ProtocolGate:
    """Thread-safe latest cooperation state shared with web supervisors."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._snapshot: CoordinationSnapshot | None = None

    def update(self, snapshot: CoordinationSnapshot) -> None:
        with self._lock:
            self._snapshot = snapshot

    def snapshot(self) -> CoordinationSnapshot | None:
        with self._lock:
            return self._snapshot

    def state_for(self, agent: str) -> AgentProtocolState | None:
        snap = self.snapshot()
        if snap is None:
            return None
        if agent == "chatgpt":
            return snap.chatgpt_protocol
        if agent == "cursor":
            return snap.cursor_protocol
        raise ValueError(f"Unknown agent: {agent}")

    def allows_own_work(self, agent: str) -> bool:
        state = self.state_for(agent)
        if state is None:
            # Fail open until the first successful coordination fetch. The UI
            # still reports that protocol state is not yet known.
            return True
        return state.state in {
            ProtocolState.OWN_WORK_ALLOWED,
            ProtocolState.READY,
        }

    def block_reason(self, agent: str) -> str:
        state = self.state_for(agent)
        if state is None:
            return ""
        if self.allows_own_work(agent):
            return ""
        return f"{state.state.value}: {state.detail}"


class GitCoordinationMonitor:
    """GitHub-native protocol monitor using the repository's existing git remote."""

    def __init__(
        self,
        repo_path: str | Path = ".",
        poll_seconds: float = 10.0,
        on_status: Callable[[str], None] | None = None,
        on_snapshot: Callable[[CoordinationSnapshot], None] | None = None,
    ) -> None:
        self.repo_path = Path(repo_path).resolve()
        self.poll_seconds = max(3.0, float(poll_seconds))
        self.on_status = on_status or (lambda _: None)
        self.on_snapshot = on_snapshot or (lambda _: None)
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

    def _branch_timestamp(self, branch: str) -> int:
        raw = self._git("log", "-1", "--format=%ct", branch)
        return int(raw or "0")

    def _path_timestamp(self, branch: str, path: str) -> int:
        if not path or path.startswith("("):
            return 0
        raw = self._git("log", "-1", "--format=%ct", branch, "--", path)
        return int(raw or "0")

    @staticmethod
    def _latest_matching(names: list[str], agent: str, kind: str) -> str:
        needle = f"-{agent}-{kind}.md"
        matches = sorted(name for name in names if name.endswith(needle))
        return matches[-1] if matches else ""

    def _protocol_state(
        self,
        agent: str,
        own_branch: str,
        peer_branch: str,
        names: list[str],
    ) -> AgentProtocolState:
        own_head = self._git("rev-parse", "--short=12", own_branch)
        peer_head = self._git("rev-parse", "--short=12", peer_branch)
        own_ts = self._branch_timestamp(own_branch)
        peer_ts = self._branch_timestamp(peer_branch)

        review_path = self._latest_matching(names, agent, "review")
        status_path = self._latest_matching(names, agent, "status")
        review_ts = self._path_timestamp("origin/coordination", review_path)
        status_ts = self._path_timestamp("origin/coordination", status_path)

        return evaluate_protocol_state(
            agent=agent,
            own_head=own_head,
            peer_head=peer_head,
            own_ts=own_ts,
            peer_ts=peer_ts,
            review_ts=review_ts,
            status_ts=status_ts,
            review_path=review_path,
            status_path=status_path,
        )

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
        markdown = sorted(
            name
            for name in names
            if name.endswith(".md") and not name.endswith("README.md")
        )
        latest = markdown[-1] if markdown else "(no coordination messages yet)"

        chatgpt_protocol = self._protocol_state(
            "chatgpt",
            "origin/agent/chatgpt",
            "origin/agent/cursor",
            markdown,
        )
        cursor_protocol = self._protocol_state(
            "cursor",
            "origin/agent/cursor",
            "origin/agent/chatgpt",
            markdown,
        )

        return CoordinationSnapshot(
            chatgpt,
            cursor,
            coord,
            latest,
            chatgpt_protocol,
            cursor_protocol,
        )

    def _describe_change(
        self,
        previous: CoordinationSnapshot | None,
        current: CoordinationSnapshot,
    ) -> str:
        protocol = (
            f"ChatGPT={current.chatgpt_protocol.state.value if current.chatgpt_protocol else 'UNKNOWN'}, "
            f"Cursor={current.cursor_protocol.state.value if current.cursor_protocol else 'UNKNOWN'}"
        )
        if previous is None:
            return (
                "Cooperation connected — "
                f"ChatGPT {current.chatgpt_head}, Cursor {current.cursor_head}, "
                f"latest {current.latest_message}; {protocol}."
            )

        changed: list[str] = []
        if current.chatgpt_head != previous.chatgpt_head:
            changed.append("ChatGPT branch updated")
        if current.cursor_head != previous.cursor_head:
            changed.append("Cursor branch updated")
        if current.coordination_head != previous.coordination_head:
            changed.append(f"coordination updated ({current.latest_message})")

        previous_states = (
            previous.chatgpt_protocol.state if previous.chatgpt_protocol else None,
            previous.cursor_protocol.state if previous.cursor_protocol else None,
        )
        current_states = (
            current.chatgpt_protocol.state if current.chatgpt_protocol else None,
            current.cursor_protocol.state if current.cursor_protocol else None,
        )
        if current_states != previous_states:
            changed.append(f"protocol {protocol}")

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
                    self.on_snapshot(current)
                    if message:
                        self.on_status(message)
                except Exception as exc:
                    # Cooperation failures are isolated from the browser runtime.
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


MESSAGE_KINDS = {"review", "status", "handoff"}


def coordination_message_filename(
    agent: str,
    kind: str,
    when: datetime | None = None,
) -> str:
    if agent not in {"chatgpt", "cursor"}:
        raise ValueError("agent must be chatgpt or cursor")
    if kind not in MESSAGE_KINDS:
        raise ValueError("kind must be review, status, or handoff")
    stamp = (when or datetime.now(timezone.utc)).astimezone(timezone.utc)
    return f"messages/{stamp:%Y%m%d-%H%M%S}-{agent}-{kind}.md"


def render_coordination_message(
    agent: str,
    kind: str,
    *,
    summary: str,
    peer_head: str = "",
    own_head: str = "",
    status: str = "",
    next_action: str = "",
) -> str:
    if kind not in MESSAGE_KINDS:
        raise ValueError("kind must be review, status, or handoff")
    lines = [
        f"# {kind.title()} — {agent}",
        "",
        f"- Agent: {agent}",
        f"- Type: {kind}",
    ]
    if status:
        lines.append(f"- Status: {status}")
    if peer_head:
        lines.append(f"- Peer head reviewed: {peer_head}")
    if own_head:
        lines.append(f"- Own head: {own_head}")
    lines.extend(["", "## Summary", "", summary.strip() or "(none)"])
    if next_action:
        lines.extend(["", "## Next action", "", next_action.strip()])
    lines.append("")
    return "\n".join(lines)
