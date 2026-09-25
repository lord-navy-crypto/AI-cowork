from __future__ import annotations

import json
import threading
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path


@dataclass
class RuntimeStatus:
    system: str = "idle"
    chatgpt: str = "idle"
    cursor: str = "idle"
    cooperation: str = "idle"
    updated_at: str = ""


class RuntimeStateStore:
    """Small crash-friendly state snapshot plus append-only event journal."""

    def __init__(
        self,
        status_path: str | Path = "state/runtime_status.json",
        events_path: str | Path = "state/events.jsonl",
        max_event_bytes: int = 2_000_000,
    ) -> None:
        self.status_path = Path(status_path)
        self.events_path = Path(events_path)
        self.max_event_bytes = max(100_000, int(max_event_bytes))
        self._lock = threading.Lock()
        self._status = self.load()

    @staticmethod
    def _now() -> str:
        return datetime.now(timezone.utc).isoformat(timespec="seconds")

    def load(self) -> RuntimeStatus:
        if not self.status_path.exists():
            return RuntimeStatus()
        try:
            raw = json.loads(self.status_path.read_text(encoding="utf-8"))
            allowed = RuntimeStatus.__dataclass_fields__.keys()
            return RuntimeStatus(**{k: raw[k] for k in allowed if k in raw})
        except Exception:
            return RuntimeStatus()

    def update(self, module: str, message: str) -> RuntimeStatus:
        if module not in {"system", "chatgpt", "cursor", "cooperation"}:
            raise ValueError(f"unknown runtime module: {module}")
        with self._lock:
            message = str(message)
            if getattr(self._status, module) == message:
                return RuntimeStatus(**asdict(self._status))
            setattr(self._status, module, message)
            self._status.updated_at = self._now()
            self._write_status_locked()
            self._append_event_locked(module, message)
            return RuntimeStatus(**asdict(self._status))

    def _write_status_locked(self) -> None:
        self.status_path.parent.mkdir(parents=True, exist_ok=True)
        temp = self.status_path.with_suffix(self.status_path.suffix + ".tmp")
        temp.write_text(
            json.dumps(asdict(self._status), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        temp.replace(self.status_path)

    def _append_event_locked(self, module: str, message: str) -> None:
        self.events_path.parent.mkdir(parents=True, exist_ok=True)
        self._rotate_events_locked()
        event = {
            "time": self._status.updated_at or self._now(),
            "module": module,
            "message": message,
        }
        with self.events_path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(event, ensure_ascii=False) + "\n")

    def _rotate_events_locked(self) -> None:
        if not self.events_path.exists():
            return
        try:
            if self.events_path.stat().st_size < self.max_event_bytes:
                return
            rotated = self.events_path.with_name(
                self.events_path.stem + ".1" + self.events_path.suffix
            )
            if rotated.exists():
                rotated.unlink()
            self.events_path.replace(rotated)
        except Exception:
            # Runtime logging must never stop the supervisors.
            return


def classify_status_message(message: str) -> str:
    text = str(message)
    if text.startswith("ChatGPT"):
        return "chatgpt"
    if text.startswith("Cursor"):
        return "cursor"
    if text.startswith("Cooperation"):
        return "cooperation"
    return "system"
