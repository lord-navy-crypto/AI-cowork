from __future__ import annotations

import objc
from AppKit import (
    NSApplication,
    NSApplicationActivationPolicyRegular,
    NSBackingStoreBuffered,
    NSButton,
    NSButtonTypeSwitch,
    NSMakeRect,
    NSTextField,
    NSWindow,
    NSWindowStyleMaskClosable,
    NSWindowStyleMaskMiniaturizable,
    NSWindowStyleMaskTitled,
)
from Foundation import NSObject

from .web_runtime import (
    SettingsStore,
    WebAutomationRuntime,
    WebSettings,
    validate_chatgpt_url,
    validate_cursor_url,
)


class WebControlWindowController(NSObject):
    def init(self):
        self = objc.super(WebControlWindowController, self).init()
        if self is None:
            return None
        self.store = SettingsStore()
        self.settings = self.store.load()
        self.runtime = None
        self.window = None
        self.chatgpt_field = None
        self.cursor_field = None
        self.status_label = None
        self.start_button = None
        self.headless_check = None
        return self

    @objc.python_method
    def build(self) -> None:
        style = (
            NSWindowStyleMaskTitled
            | NSWindowStyleMaskClosable
            | NSWindowStyleMaskMiniaturizable
        )
        self.window = NSWindow.alloc().initWithContentRect_styleMask_backing_defer_(
            NSMakeRect(0, 0, 620, 350),
            style,
            NSBackingStoreBuffered,
            False,
        )
        self.window.setTitle_("AI-cowork Web Controller")
        self.window.center()
        self.window.setDelegate_(self)
        content = self.window.contentView()

        content.addSubview_(self._label("AI-cowork Web Controller", 24, 298, 570, 30, 21))
        content.addSubview_(
            self._label(
                "Dedicated ChatGPT + Cursor web sessions. Passwords and cookies stay in the browser profile.",
                24, 270, 570, 20, 12,
            )
        )

        content.addSubview_(self._label("ChatGPT work URL", 24, 224, 170, 20, 13))
        self.chatgpt_field = self._text_field(
            self.settings.chatgpt_url,
            24, 192, 570, 28,
        )
        content.addSubview_(self.chatgpt_field)

        content.addSubview_(self._label("Cursor Agent URL", 24, 155, 170, 20, 13))
        self.cursor_field = self._text_field(
            self.settings.cursor_url,
            24, 123, 570, 28,
        )
        content.addSubview_(self.cursor_field)

        self.headless_check = NSButton.alloc().initWithFrame_(NSMakeRect(24, 91, 240, 24))
        self.headless_check.setButtonType_(NSButtonTypeSwitch)
        self.headless_check.setTitle_("Run hidden after login")
        self.headless_check.setState_(1 if self.settings.headless else 0)
        content.addSubview_(self.headless_check)

        save_button = self._button("Save URLs", 24, 50, 145, 34, "saveURLs:")
        content.addSubview_(save_button)

        self.start_button = self._button(
            "Start Web Supervisor",
            183, 50, 200, 34,
            "toggleSupervisor:",
        )
        content.addSubview_(self.start_button)

        browser_button = self._button(
            "Open/Login Session",
            397, 50, 197, 34,
            "openSession:",
        )
        content.addSubview_(browser_button)

        self.status_label = self._label("Status: idle", 24, 17, 570, 24, 12)
        content.addSubview_(self.status_label)

        self.window.makeKeyAndOrderFront_(None)

    @objc.python_method
    def _label(self, text: str, x: float, y: float, w: float, h: float, size: float):
        label = NSTextField.alloc().initWithFrame_(NSMakeRect(x, y, w, h))
        label.setStringValue_(text)
        label.setEditable_(False)
        label.setSelectable_(False)
        label.setBordered_(False)
        label.setDrawsBackground_(False)
        label.setFont_(label.font().fontWithSize_(size))
        return label

    @objc.python_method
    def _text_field(self, text: str, x: float, y: float, w: float, h: float):
        field = NSTextField.alloc().initWithFrame_(NSMakeRect(x, y, w, h))
        field.setStringValue_(text or "")
        return field

    @objc.python_method
    def _button(self, title: str, x: float, y: float, w: float, h: float, action: str):
        button = NSButton.alloc().initWithFrame_(NSMakeRect(x, y, w, h))
        button.setTitle_(title)
        button.setTarget_(self)
        button.setAction_(action)
        return button

    @objc.python_method
    def _collect_settings(self) -> WebSettings:
        chatgpt_url = self.chatgpt_field.stringValue().strip()
        cursor_url = self.cursor_field.stringValue().strip()
        current = self.store.load()
        current.chatgpt_url = chatgpt_url
        current.cursor_url = cursor_url
        current.headless = bool(self.headless_check.state()) if self.headless_check is not None else False
        return current

    @objc.python_method
    def _validate(self, settings: WebSettings) -> str | None:
        if not validate_chatgpt_url(settings.chatgpt_url):
            return "Enter a valid ChatGPT URL beginning with https://chatgpt.com/."
        if settings.cursor_url and not validate_cursor_url(settings.cursor_url):
            return "Enter a valid Cursor URL beginning with https://cursor.com/."
        return None

    @objc.python_method
    def _save(self) -> WebSettings | None:
        settings = self._collect_settings()
        error = self._validate(settings)
        if error:
            self.updateStatus_(error)
            return None
        self.store.save(settings)
        self.settings = settings
        self.updateStatus_("URLs saved.")
        return settings

    @objc.IBAction
    def saveURLs_(self, sender):
        self._save()

    @objc.IBAction
    def openSession_(self, sender):
        settings = self._save()
        if settings is None:
            return
        if self.runtime and self.runtime.running:
            self.updateStatus_("Dedicated browser session is already running.")
            return
        # Login sessions are always visible so the user can authenticate.
        settings.headless = False
        self.runtime = WebAutomationRuntime(
            settings,
            on_status=self.status_from_worker,
            supervise=False,
        )
        try:
            self.runtime.start()
            self.start_button.setTitle_("Stop Session")
            self.updateStatus_("Opening dedicated browser for login/connection only.")
        except Exception as exc:
            self.updateStatus_(f"Start error: {exc}")

    @objc.IBAction
    def toggleSupervisor_(self, sender):
        if self.runtime and self.runtime.running:
            self.runtime.stop()
            self.start_button.setTitle_("Start Web Supervisor")
            return

        settings = self._save()
        if settings is None:
            return

        self.runtime = WebAutomationRuntime(settings, on_status=self.status_from_worker)
        try:
            self.runtime.start()
            self.start_button.setTitle_("Stop Web Supervisor")
            self.updateStatus_("Web Supervisor starting…")
        except Exception as exc:
            self.updateStatus_(f"Start error: {exc}")

    @objc.python_method
    def status_from_worker(self, message: str) -> None:
        self.performSelectorOnMainThread_withObject_waitUntilDone_(
            "updateStatus:", message, False
        )

    def updateStatus_(self, message):
        if self.status_label is not None:
            self.status_label.setStringValue_(f"Status: {message}")
        if self.start_button is not None and not (
            self.runtime and self.runtime.running
        ):
            self.start_button.setTitle_("Start Web Supervisor")

    def windowWillClose_(self, notification):
        if self.runtime and self.runtime.running:
            self.runtime.stop()
        NSApplication.sharedApplication().terminate_(None)


_controller_ref = None


def run_gui() -> None:
    global _controller_ref
    app = NSApplication.sharedApplication()
    app.setActivationPolicy_(NSApplicationActivationPolicyRegular)
    controller = WebControlWindowController.alloc().init()
    controller.build()
    _controller_ref = controller
    app.activateIgnoringOtherApps_(True)
    app.run()
