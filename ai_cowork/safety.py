from __future__ import annotations

from dataclasses import dataclass


DEFAULT_BLOCKED = (
    "git push --force",
    "git push -f",
    "git reset --hard",
    "rm -rf",
    "git checkout -- .",
    "git clean -fd",
    "git branch -D",
    "git merge main",
)


@dataclass
class SafetyResult:
    allowed: bool
    reason: str = ""


class SafetyGuard:
    def __init__(self, blocked_fragments: list[str] | None = None):
        self.blocked = tuple(blocked_fragments or DEFAULT_BLOCKED)

    def check_text(self, text: str) -> SafetyResult:
        low = text.lower()
        for fragment in self.blocked:
            if fragment.lower() in low:
                return SafetyResult(False, f"blocked command fragment: {fragment}")
        return SafetyResult(True)

    def require_safe(self, text: str) -> None:
        result = self.check_text(text)
        if not result.allowed:
            raise RuntimeError(result.reason)
