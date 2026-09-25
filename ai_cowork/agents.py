from __future__ import annotations

import time
from abc import ABC, abstractmethod
from dataclasses import dataclass

from .events import EventLog
from .macos import paste_and_enter, text_snapshot


class Agent(ABC):
    @abstractmethod
    def send(self, prompt: str) -> None: ...

    @abstractmethod
    def read_snapshot(self) -> str: ...

    @abstractmethod
    def wait_until_stable(self, timeout: float = 300) -> str: ...


@dataclass
class DesktopAgent(Agent):
    name: str
    app_name: str
    poll_interval: float = 1.0
    stable_seconds: float = 4.0
    enter_to_send: bool = True
    events: EventLog | None = None

    def send(self, prompt: str) -> None:
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
                    if self.events:
                        self.events.emit(
                            "agent_stable",
                            agent=self.name,
                            chars=len(current),
                        )
                    return current
            else:
                previous = current
                stable_since = None

            time.sleep(self.poll_interval)

        raise TimeoutError(f"{self.name} output did not become stable")


class BrowserAgent(DesktopAgent):
    """MVP browser adapter.

    For v0.1 this intentionally reuses macOS Accessibility against a dedicated
    browser window. A DOM/Playwright adapter can later replace it without
    changing the Controller interface.
    """

    window_title_contains: str | None = None
