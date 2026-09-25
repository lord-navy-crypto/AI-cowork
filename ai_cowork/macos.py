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


MEANINGFUL_ROLES = {
    "AXTextArea", "AXTextField", "AXStaticText", "AXButton", "AXLink",
    "AXHeading", "AXWebArea", "AXCheckBox", "AXRadioButton",
    "AXList", "AXListItem", "AXScrollArea"
}


def meaningful_accessibility_dump(app_name: str, max_depth: int = 30, max_nodes: int = 12000) -> str:
    """Deep dump focused on actionable/text-bearing nodes plus a role histogram."""
    pid = find_pid(app_name)
    if pid is None:
        raise RuntimeError(f"{app_name} is not running")
    enable_enhanced_accessibility(app_name)
    root = AXUIElementCreateApplication(pid)

    seen: set[str] = set()
    lines: list[str] = []
    counts: dict[str, int] = {}
    visited = 0

    def visit(node, depth: int) -> None:
        nonlocal visited
        if depth > max_depth or visited >= max_nodes:
            return
        ident = str(node)
        if ident in seen:
            return
        seen.add(ident)
        visited += 1

        role = str(_attr(node, "AXRole") or "")
        counts[role] = counts.get(role, 0) + 1

        title = str(_attr(node, "AXTitle") or "")
        value = str(_attr(node, "AXValue") or "")
        desc = str(_attr(node, "AXDescription") or "")
        placeholder = str(_attr(node, "AXPlaceholderValue") or "")
        domid = str(_attr(node, "AXDOMIdentifier") or "")
        classes = _attr(node, "AXDOMClassList")
        class_text = ""
        if classes is not None and not isinstance(classes, (str, bytes)):
            try:
                class_text = " ".join(str(x) for x in list(classes))
            except TypeError:
                class_text = str(classes)

        if role in MEANINGFUL_ROLES or any((title, value, desc, placeholder, domid)):
            bits = []
            if title: bits.append(f"title={title[:160]!r}")
            if value: bits.append(f"value={value[:240]!r}")
            if desc: bits.append(f"desc={desc[:160]!r}")
            if placeholder: bits.append(f"placeholder={placeholder[:160]!r}")
            if domid: bits.append(f"id={domid[:120]!r}")
            if class_text: bits.append(f"class={class_text[:180]!r}")
            lines.append(f'{"  " * min(depth, 20)}{role}: ' + " | ".join(bits))

        children: list = []
        for attr_name in ("AXChildren", "AXContents", "AXRows", "AXWindows", "AXChildrenInNavigationOrder"):
            obj = _attr(node, attr_name)
            if obj is None or isinstance(obj, (str, bytes)):
                continue
            try:
                children.extend(list(obj))
            except TypeError:
                pass
        for child in children:
            visit(child, depth + 1)

    visit(root, 0)
    histogram = "\n".join(
        f"{role or '(none)'}={count}"
        for role, count in sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
    )
    return (
        f"visited_nodes={visited}\n"
        f"===== ROLE HISTOGRAM =====\n{histogram}\n"
        f"===== MEANINGFUL NODES =====\n" + "\n".join(lines)
    )
