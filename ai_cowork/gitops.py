from __future__ import annotations

import subprocess
from dataclasses import dataclass
from pathlib import Path

from .safety import SafetyGuard


def _run(args: list[str], cwd: Path) -> str:
    p = subprocess.run(args, cwd=cwd, text=True, capture_output=True, check=True)
    return p.stdout.strip()


@dataclass
class WorktreeManager:
    repo: Path
    guard: SafetyGuard

    def ensure_clean(self) -> None:
        status = _run(["git", "status", "--porcelain"], self.repo)
        if status:
            raise RuntimeError("repository has uncommitted changes; refusing worktree setup")

    def ensure_branch(self, branch: str, base: str = "main") -> None:
        existing = _run(["git", "branch", "--list", branch], self.repo)
        if not existing:
            _run(["git", "branch", branch, base], self.repo)

    def ensure_worktree(self, branch: str, path: Path) -> None:
        self.guard.require_safe(f"git worktree add {path} {branch}")
        self.ensure_branch(branch)
        if path.exists() and any(path.iterdir()):
            return
        path.parent.mkdir(parents=True, exist_ok=True)
        _run(["git", "worktree", "add", str(path), branch], self.repo)

    def summary(self, path: Path) -> str:
        status = _run(["git", "status", "--short"], path)
        log = _run(["git", "log", "-1", "--oneline"], path)
        diff = _run(["git", "diff", "--stat"], path)
        return "\n".join([
            f"commit: {log}",
            "status:",
            status or "(clean)",
            "diff:",
            diff or "(none)",
        ])
