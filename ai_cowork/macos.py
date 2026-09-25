from __future__ import annotations

import subprocess
import time
from dataclasses import dataclass

from ApplicationServices import (
    AXIsProcessTrusted,
    AXUIElementCopyAttributeNames,
    AXUIElementCopyAttributeValue,
    AXUIElementCreateApplication,
    AXUIElementSetAttributeValue,
)
from AppKit import NSWorkspace


TEXT_ATTRIBUTES = ("AXValue", "AXTitle", "AXDescription")
CHILD_ATTRIBUTES = ("AXChildren", "AXContents", "AXRows", "AXWindows")


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


def accessibility_trusted() -> bool:
    try:
        return bool(AXIsProcessTrusted())
    except Exception:
        return False


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


def system_events_probe(app_name: str) -> dict[str, str]:
    escaped = app_name.replace('"', '\\"')
    script = f'''
    tell application "System Events"
        if not (exists process "{escaped}") then return "PROCESS_MISSING"
        tell process "{escaped}"
            set frontmost to true
            set wc to count of windows
            set ec to count of UI elements
            set namesText to ""
            repeat with w in windows
                try
                    set namesText to namesText & (name of w as text) & " | "
                end try
            end repeat
            return "windows=" & wc & "; root_ui_elements=" & ec & "; window_names=" & namesText
        end tell
    end tell
    '''
    try:
        return {"ok": "true", "output": run_osascript(script)}
    except subprocess.CalledProcessError as e:
        return {
            "ok": "false",
            "output": (e.stderr or e.stdout or str(e)).strip(),
        }


def _attr(element, name: str):
    try:
        err, value = AXUIElementCopyAttributeValue(element, name, None)
        if err == 0:
            return value
    except Exception:
        pass
    return None


def _attr_names(element) -> list[str]:
    try:
        err, names = AXUIElementCopyAttributeNames(element, None)
        if err == 0 and names:
            return [str(x) for x in names]
    except Exception:
        pass
    return []


def enable_enhanced_accessibility(app_name: str) -> bool:
    pid = find_pid(app_name)
    if pid is None:
        return False
    app = AXUIElementCreateApplication(pid)
    # Electron/Chromium apps may keep the deep web accessibility tree lazy
    # until an assistive technology requests enhanced UI.
    for attr in ("AXEnhancedUserInterface", "AXManualAccessibility"):
        try:
            AXUIElementSetAttributeValue(app, attr, True)
        except Exception:
            pass
    return True


@dataclass
class AXNode:
    role: str
    value: str
    depth: int


def walk_accessibility(app_name: str, max_depth: int = 12, max_nodes: int = 4000) -> list[AXNode]:
    pid = find_pid(app_name)
    if pid is None:
        raise RuntimeError(f"{app_name} is not running")

    enable_enhanced_accessibility(app_name)
    root = AXUIElementCreateApplication(pid)
    out: list[AXNode] = []
    seen: set[str] = set()

    def visit(node, depth: int) -> None:
        if depth > max_depth or len(out) >= max_nodes:
            return

        ident = str(node)
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
            if value is None:
                continue
            # PyObjC commonly returns NSArray/CFArray proxy objects rather than
            # native Python list/tuple instances. Treat any non-string iterable
            # as a child collection.
            if isinstance(value, (str, bytes)):
                continue
            try:
                children.extend(list(value))
            except TypeError:
                pass

        for child in children:
            visit(child, depth + 1)

    visit(root, 0)
    return out


def text_snapshot(app_name: str) -> str:
    nodes = walk_accessibility(app_name)
    return "\n".join(node.value for node in nodes if node.value)


def formatted_tree(app_name: str) -> str:
    nodes = walk_accessibility(app_name)
    return "\n".join(
        f'{"  " * node.depth}{node.role}: {node.value[:240]}'
        for node in nodes
    )


def accessibility_debug(app_name: str, max_depth: int = 10) -> str:
    """Dump roles plus available AX attributes for WebArea/text-like nodes."""
    pid = find_pid(app_name)
    if pid is None:
        raise RuntimeError(f"{app_name} is not running")
    enable_enhanced_accessibility(app_name)
    root = AXUIElementCreateApplication(pid)
    lines: list[str] = []
    seen: set[str] = set()

    def visit(node, depth: int) -> None:
        if depth > max_depth:
            return
        ident = str(node)
        if ident in seen:
            return
        seen.add(ident)

        role = str(_attr(node, "AXRole") or "")
        value = str(_attr(node, "AXTitle") or _attr(node, "AXValue") or "")
        if role in ("AXWebArea", "AXTextArea", "AXStaticText", "AXGroup"):
            names = ",".join(_attr_names(node))
            lines.append(f'{"  " * depth}{role}: {value[:120]}')
            lines.append(f'{"  " * depth}  attrs=[{names}]')

        children: list = []
        for attr_name in ("AXChildren", "AXContents", "AXRows", "AXWindows", "AXChildrenInNavigationOrder"):
            value_obj = _attr(node, attr_name)
            if value_obj is None or isinstance(value_obj, (str, bytes)):
                continue
            try:
                children.extend(list(value_obj))
            except TypeError:
                pass
        for child in children:
            visit(child, depth + 1)

    visit(root, 0)
    return "\n".join(lines)
