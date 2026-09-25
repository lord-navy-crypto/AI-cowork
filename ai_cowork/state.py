from __future__ import annotations

import json
from dataclasses import dataclass, asdict
from enum import Enum
from pathlib import Path
from typing import Any


class AgentState(str, Enum):
    IDLE = "IDLE"
    WORKING = "WORKING"
    WAITING = "WAITING"
    REPORTING = "REPORTING"
    REVIEWING = "REVIEWING"
    BLOCKED = "BLOCKED"
    ERROR = "ERROR"
    DONE = "DONE"


@dataclass
class AgentRuntime:
    name: str
    state: AgentState = AgentState.IDLE
    round: int = 0
    last_text: str = ""
    last_error: str = ""


class RuntimeStore:
    def __init__(self, path: str | Path = "state/runtime.json"):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.agents: dict[str, AgentRuntime] = {}

    def get(self, name: str) -> AgentRuntime:
        if name not in self.agents:
            self.agents[name] = AgentRuntime(name=name)
        return self.agents[name]

    def load(self) -> None:
        if not self.path.exists():
            return
        raw = json.loads(self.path.read_text())
        self.agents = {
            name: AgentRuntime(
                name=name,
                state=AgentState(item.get("state", "IDLE")),
                round=item.get("round", 0),
                last_text=item.get("last_text", ""),
                last_error=item.get("last_error", ""),
            )
            for name, item in raw.get("agents", {}).items()
        }

    def save(self) -> None:
        payload: dict[str, Any] = {
            "agents": {name: asdict(runtime) for name, runtime in self.agents.items()}
        }
        self.path.write_text(json.dumps(payload, indent=2, ensure_ascii=False))
