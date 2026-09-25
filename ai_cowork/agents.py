from __future__ import annotations

import time
from abc import ABC, abstractmethod
from dataclasses import dataclass, field

from .events import EventLog
from .macos import paste_and_enter, text_snapshot


class Agent(ABC):
    @abstractmethod
    def send(self, prompt: str) -> None: ...

    @abstractmethod
    def read_snapshot(self) -> str: ...

    @abstractmethod
    def wait_until_stable(self, timeout: float = 300) -> str: ...


def _extract_delta(before: str, after: str) -> str:
    """Best-effort extraction of newly appeared UI text.

    Accessibility snapshots are flattened text, not a semantic message list.
    We therefore remove the longest common prefix. If the app reorders the
    accessibility tree, fall back to the full stable snapshot rather than
    returning an empty response.
    """
    limit = min(len(before), len(after))
    i = 0
    while i < limit and before[i] == after[i]:
        i += 1
    delta = after[i:].strip()
    return delta or after.strip()


@dataclass
class DesktopAgent(Agent):
    name: str
    app_name: str
    poll_interval: float = 1.0
    stable_seconds: float = 4.0
    enter_to_send: bool = True
    events: EventLog | None = None
    _baseline: str = field(default="", init=False, repr=False)

    def send(self, prompt: str) -> None:
        # Capture the pre-send UI so wait_until_stable can return only the
        # newly produced content instead of forwarding the entire conversation.
        try:
            self._baseline = self.read_snapshot()
        except Exception:
            self._baseline = ""

        if self.events:
            self.events.emit("agent_send", agent=self.name, chars=len(prompt))
        paste_and_enter(self.app_name, prompt, enter=self.enter_to_send)

    def read_snapshot(self) -> str:
        return text_snapshot(self.app_name)

    def wait_until_stable(self, timeout: float = 300) -> str:
        started = time.monotonic()
        previous = ""
        stable_since: float | None = None

        while time.monotonic() - started < timeout:
            current = self.read_snapshot()

            if current and current == previous:
                if stable_since is None:
                    stable_since = time.monotonic()
                if time.monotonic() - stable_since >= self.stable_seconds:
                    output = _extract_delta(self._baseline, current)
                    if self.events:
                        self.events.emit(
                            "agent_stable",
                            agent=self.name,
                            snapshot_chars=len(current),
                            output_chars=len(output),
                        )
                    return output
            else:
                previous = current
                stable_since = None

            time.sleep(self.poll_interval)

        raise TimeoutError(f"{self.name} output did not become stable")


@dataclass
class BrowserAgent(DesktopAgent):
    """MVP browser adapter.

    v0.1 reuses macOS Accessibility against a dedicated browser window.
    A DOM/Playwright adapter can later replace it without changing the
    Controller interface.
    """

    window_title_contains: str | None = None
