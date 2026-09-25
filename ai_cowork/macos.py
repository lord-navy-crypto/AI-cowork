from __future__ import annotations

import subprocess
import time
from dataclasses import dataclass
from typing import Iterable

from ApplicationServices import (
    AXUIElementCopyAttributeValue,
    AXUIElementCreateApplication,
)
from AppKit import NSWorkspace


TEXT_ATTRIBUTES = ("AXValue", "AXTitle", "AXDescription")
CHILD_ATTRIBUTES = ("AXChildren", "AXContents", "AXRows")


def run_osascript(script: str) -> str:
    p = subprocess.run(
        ["osascript", "-e", script],
        text=True,
        capture_output=True,
        check=True,
    )
    return p.stdout.strip()


def set_clipboard(text: str) -> None:
    subprocess.run(["pbcopy"], input=text, text=True, check=True)


def get_clipboard() -> str:
    return subprocess.run(
        ["pbpaste"], text=True, capture_output=True, check=True
    ).stdout


def find_pid(app_name: str) -> int | None:
    workspace = NSWorkspace.sharedWorkspace()
    target = app_name.casefold()
    for app in workspace.runningApplications():
        name = (app.localizedName() or "").casefold()
        if name == target:
            return int(app.processIdentifier())
    return None


def activate_app(app_name: str) -> None:
    escaped = app_name.replace('"', '\\"')
    run_osascript(f'tell application "{escaped}" to activate')


def paste_and_enter(app_name: str, text: str, enter: bool = True) -> None:
    set_clipboard(text)
    activate_app(app_name)
    time.sleep(0.25)
    enter_line = "key code 36" if enter else ""
    run_osascript(
        f'''
        tell application "System Events"
            keystroke "v" using command down
            delay 0.1
            {enter_line}
        end tell
        '''
    )


def _attr(element, name: str):
    try:
        err, value = AXUIElementCopyAttributeValue(element, name, None)
        if err == 0:
            return value
    except Exception:
        pass
    return None


@dataclass
class AXNode:
    role: str
    value: str
    depth: int


def walk_accessibility(app_name: str, max_depth: int = 12, max_nodes: int = 4000) -> list[AXNode]:
    pid = find_pid(app_name)
    if pid is None:
        raise RuntimeError(f"{app_name} is not running")

    root = AXUIElementCreateApplication(pid)
    out: list[AXNode] = []
    seen: set[int] = set()

    def visit(node, depth: int) -> None:
        if depth > max_depth or len(out) >= max_nodes:
            return

        ident = id(node)
        if ident in seen:
            return
        seen.add(ident)

        role = str(_attr(node, "AXRole") or "")
        pieces: list[str] = []
        for attr_name in TEXT_ATTRIBUTES:
            value = _attr(node, attr_name)
            if isinstance(value, str) and value.strip():
                pieces.append(value.strip())

        unique = []
        for item in pieces:
            if item not in unique:
                unique.append(item)

        out.append(AXNode(role=role, value=" | ".join(unique), depth=depth))

        children: list = []
        for attr_name in CHILD_ATTRIBUTES:
            value = _attr(node, attr_name)
            if isinstance(value, (list, tuple)):
                children.extend(value)

        for child in children:
            visit(child, depth + 1)

    visit(root, 0)
    return out


def text_snapshot(app_name: str) -> str:
    nodes = walk_accessibility(app_name)
    lines: list[str] = []
    for node in nodes:
        if node.value:
            lines.append(node.value)
    return "\n".join(lines)


def formatted_tree(app_name: str) -> str:
    nodes = walk_accessibility(app_name)
    return "\n".join(
        f'{"  " * node.depth}{node.role}: {node.value[:240]}'
        for node in nodes
    )
