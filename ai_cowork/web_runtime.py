from __future__ import annotations

import json
import re
import threading
import time
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Callable
from urllib.parse import urlparse

from playwright.sync_api import BrowserContext, Page, Playwright, sync_playwright


CHATGPT_HOSTS = {"chatgpt.com", "www.chatgpt.com"}
CURSOR_HOSTS = {"cursor.com", "www.cursor.com"}

CONTEXT_LIMIT_MARKERS = (
    "maximum context length",
    "context window is full",
    "context window limit",
    "reached the context limit",
    "reached the maximum length for this conversation",
    "conversation is too long",
    "start a new chat to continue",
    "continue in a new chat",
    "上下文窗口已满",
    "上下文长度已达到",
    "已达到上下文",
    "对话太长",
    "请开始新对话",
    "新对话中继续",
)


def _valid_url_for(url: str, hosts: set[str]) -> bool:
    try:
        parsed = urlparse(url.strip())
    except Exception:
        return False
    return parsed.scheme == "https" and (parsed.hostname or "").casefold() in hosts


def validate_chatgpt_url(url: str) -> bool:
    return _valid_url_for(url, CHATGPT_HOSTS)


def validate_cursor_url(url: str) -> bool:
    return _valid_url_for(url, CURSOR_HOSTS)


def context_limit_detected(text: str) -> bool:
    normalized = " ".join((text or "").casefold().split())
    return any(marker.casefold() in normalized for marker in CONTEXT_LIMIT_MARKERS)


@dataclass
class WebSettings:
    chatgpt_url: str = ""
    cursor_url: str = ""
    continue_prompt: str = (
        "Continue doing the current task. Keep working from where you stopped. "
        "Do not restart or summarize unless necessary; continue the actual work."
    )
    idle_confirm_seconds: float = 3.0
    poll_interval_seconds: float = 1.0
    headless: bool = False
    profile_dir: str = "state/browser-profile"


class SettingsStore:
    def __init__(self, path: str | Path = "state/web_settings.json") -> None:
        self.path = Path(path)

    def load(self) -> WebSettings:
        if not self.path.exists():
            return WebSettings()
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
            allowed = {field for field in WebSettings.__dataclass_fields__}
            return WebSettings(**{k: v for k, v in raw.items() if k in allowed})
        except Exception:
            return WebSettings()

    def save(self, settings: WebSettings) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(
            json.dumps(asdict(settings), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


class WebAutomationRuntime:
    """One persistent Chromium profile for ChatGPT and Cursor work pages."""

    def __init__(
        self,
        settings: WebSettings,
        on_status: Callable[[str], None] | None = None,
        supervise: bool = True,
    ) -> None:
        self.settings = settings
        self.supervise = supervise
        self.on_status = on_status or (lambda _: None)
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._playwright: Playwright | None = None
        self._context: BrowserContext | None = None
        self._chatgpt_page: Page | None = None
        self._cursor_page: Page | None = None

    @property
    def running(self) -> bool:
        return bool(self._thread and self._thread.is_alive())

    def start(self) -> bool:
        if self.running:
            return False
        if not validate_chatgpt_url(self.settings.chatgpt_url):
            raise ValueError("Please enter a valid https://chatgpt.com/... URL.")
        if self.settings.cursor_url and not validate_cursor_url(self.settings.cursor_url):
            raise ValueError("Please enter a valid https://cursor.com/... URL.")

        self._stop.clear()
        self._thread = threading.Thread(
            target=self._run,
            name="ai-cowork-web-runtime",
            daemon=True,
        )
        self._thread.start()
        return True

    def stop(self) -> None:
        self._stop.set()
        self._status("Stopping web supervisor…")

    def _status(self, message: str) -> None:
        self.on_status(message)

    def _launch(self) -> None:
        profile = Path(self.settings.profile_dir).expanduser().resolve()
        profile.mkdir(parents=True, exist_ok=True)
        self._playwright = sync_playwright().start()
        self._context = self._playwright.chromium.launch_persistent_context(
            user_data_dir=str(profile),
            headless=bool(self.settings.headless),
            viewport={"width": 1280, "height": 900},
            args=["--disable-background-timer-throttling"],
        )
        pages = list(self._context.pages)
        self._chatgpt_page = pages[0] if pages else self._context.new_page()
        self._chatgpt_page.goto(
            self.settings.chatgpt_url,
            wait_until="domcontentloaded",
            timeout=60_000,
        )

        if self.settings.cursor_url:
            self._cursor_page = self._context.new_page()
            self._cursor_page.goto(
                self.settings.cursor_url,
                wait_until="domcontentloaded",
                timeout=60_000,
            )

    def _close(self) -> None:
        try:
            if self._context is not None:
                self._context.close()
        except Exception:
            pass
        try:
            if self._playwright is not None:
                self._playwright.stop()
        except Exception:
            pass
        self._context = None
        self._playwright = None
        self._chatgpt_page = None
        self._cursor_page = None

    @staticmethod
    def _page_text(page: Page) -> str:
        try:
            return page.locator("body").inner_text(timeout=5_000)
        except Exception:
            return ""

    @staticmethod
    def _login_required(page: Page) -> bool:
        text = WebAutomationRuntime._page_text(page).casefold()
        url = page.url.casefold()
        markers = ("log in", "sign up", "登录", "注册")
        return "/auth/" in url or any(marker in text[:2500] for marker in markers)

    @staticmethod
    def _chatgpt_generating(page: Page) -> bool:
        selectors = (
            'button[data-testid="stop-button"]',
            'button[aria-label*="Stop"]',
            'button[title*="Stop"]',
        )
        for selector in selectors:
            try:
                if page.locator(selector).first.is_visible(timeout=250):
                    return True
            except Exception:
                pass

        try:
            buttons = page.get_by_role("button")
            count = min(buttons.count(), 100)
            for i in range(count):
                try:
                    label = (
                        buttons.nth(i).get_attribute("aria-label")
                        or buttons.nth(i).get_attribute("title")
                        or ""
                    ).casefold()
                    if any(token in label for token in ("stop generating", "stop response", "stop streaming")):
                        return True
                except Exception:
                    continue
        except Exception:
            pass
        return False

    @staticmethod
    def _find_prompt(page: Page):
        candidates = (
            "#prompt-textarea",
            '[contenteditable="true"][data-virtualkeyboard="true"]',
            '[contenteditable="true"]',
            "textarea",
        )
        for selector in candidates:
            locator = page.locator(selector).last
            try:
                if locator.is_visible(timeout=400) and locator.is_editable(timeout=400):
                    return locator
            except Exception:
                continue
        return None

    @staticmethod
    def _send_prompt(page: Page, text: str) -> None:
        prompt = WebAutomationRuntime._find_prompt(page)
        if prompt is None:
            raise RuntimeError("ChatGPT prompt box was not found. The page may need login or the UI changed.")

        prompt.fill(text)

        selectors = (
            'button[data-testid="send-button"]',
            'button[aria-label*="Send"]',
            'button[title*="Send"]',
        )
        for selector in selectors:
            button = page.locator(selector).first
            try:
                if button.is_visible(timeout=350) and button.is_enabled(timeout=350):
                    button.click()
                    return
            except Exception:
                continue

        prompt.press("Enter")

    def _cursor_summary(self) -> str:
        page = self._cursor_page
        if page is None:
            return "Cursor URL not configured."
        if self._login_required(page):
            return "Cursor login required in the dedicated browser."
        text = self._page_text(page)
        lowered = text.casefold()
        if any(word in lowered for word in ("running", "working", "agent is working", "stop agent")):
            return "Cursor connected — agent appears active."
        if any(word in lowered for word in ("completed", "done", "finished")):
            return "Cursor connected — page appears idle/completed."
        return "Cursor connected."

    def _wait_for_generation_cycle(self, page: Page) -> None:
        start_deadline = time.monotonic() + 20.0
        saw_generating = False
        while not self._stop.is_set() and time.monotonic() < start_deadline:
            if self._chatgpt_generating(page):
                saw_generating = True
                break
            self._stop.wait(0.5)

        if not saw_generating:
            # Some UI versions transition too quickly to expose the stop button.
            # A cooldown prevents duplicate continue sends.
            self._stop.wait(max(4.0, self.settings.idle_confirm_seconds))

        idle_since: float | None = None
        while not self._stop.is_set():
            if self._chatgpt_generating(page):
                idle_since = None
                self._status("ChatGPT Web is working — waiting.")
            else:
                now = time.monotonic()
                if idle_since is None:
                    idle_since = now
                elif now - idle_since >= self.settings.idle_confirm_seconds:
                    return
            self._stop.wait(max(0.5, self.settings.poll_interval_seconds))

    def _run(self) -> None:
        try:
            self._status("Opening dedicated web session…")
            self._launch()
            page = self._chatgpt_page
            if page is None:
                raise RuntimeError("ChatGPT page could not be created.")

            if self._login_required(page):
                self._status("ChatGPT login required — sign in in the dedicated browser, then leave it open.")
                while not self._stop.is_set() and self._login_required(page):
                    self._stop.wait(2.0)

            if self._stop.is_set():
                return

            self._status(self._cursor_summary())
            if not self.supervise:
                self._status("Dedicated web session connected — login state will be saved locally.")
                while not self._stop.is_set():
                    self._stop.wait(1.0)
                return

            self._status("ChatGPT Web connected — supervisor active.")

            while not self._stop.is_set():
                page_text = self._page_text(page)
                if context_limit_detected(page_text):
                    self._status("Context limit detected — supervisor stopped.")
                    break

                if self._chatgpt_generating(page):
                    self._status("ChatGPT Web is working — waiting.")
                    self._stop.wait(max(0.5, self.settings.poll_interval_seconds))
                    continue

                self._status("ChatGPT Web is idle — sending continue.")
                self._send_prompt(page, self.settings.continue_prompt)
                self._wait_for_generation_cycle(page)

        except Exception as exc:
            self._status(f"Web supervisor error: {exc}")
        finally:
            self._close()
            self._thread = None
            if self._stop.is_set():
                self._status("Web supervisor stopped.")
