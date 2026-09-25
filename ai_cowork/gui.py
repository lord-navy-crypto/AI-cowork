from __future__ import annotations

import objc
from AppKit import (
    NSApp,
    NSApplication,
    NSApplicationActivationPolicyRegular,
    NSBackingStoreBuffered,
    NSButton,
    NSMakeRect,
    NSObject,
    NSTextField,
    NSWindow,
    NSWindowStyleMaskClosable,
    NSWindowStyleMaskMiniaturizable,
    NSWindowStyleMaskTitled,
)

from .macos import notify_user
from .supervisor import ChatGPTSupervisor


class SupervisorWindowController(NSObject):
    def initWithSupervisor_(self, supervisor: ChatGPTSupervisor):
        self = objc.super(SupervisorWindowController, self).init()
        if self is None:
            return None
        self.supervisor = supervisor
        self.window = None
        self.status_label = None
        self.supervisor_button = None
        self.supervisor.on_status = self.status_from_worker
        return self

    def build(self) -> None:
        style = (
            NSWindowStyleMaskTitled
            | NSWindowStyleMaskClosable
            | NSWindowStyleMaskMiniaturizable
        )
        self.window = NSWindow.alloc().initWithContentRect_styleMask_backing_defer_(
            NSMakeRect(0, 0, 520, 320),
            style,
            NSBackingStoreBuffered,
            False,
        )
        self.window.setTitle_("AI-cowork")
        self.window.center()
        self.window.setDelegate_(self)

        content = self.window.contentView()

        title = self._label("ChatGPT Work Supervisor", 24, 270, 472, 30, 20)
        subtitle = self._label(
            "Keep ChatGPT working without watching the window continuously.",
            24, 240, 472, 22, 13,
        )
        content.addSubview_(title)
        content.addSubview_(subtitle)

        self.status_label = self._label("Status: idle", 24, 202, 472, 24, 13)
        content.addSubview_(self.status_label)

        self.supervisor_button = self._button(
            "Start ChatGPT Supervisor", 24, 142, 472, 44, "toggleSupervisor:"
        )
        content.addSubview_(self.supervisor_button)

        future_one = self._button(
            "Multi-agent collaboration — Under development",
            24, 86, 472, 40, "futureFeature:"
        )
        content.addSubview_(future_one)

        future_two = self._button(
            "Repository automation — Under development",
            24, 34, 472, 40, "futureFeature:"
        )
        content.addSubview_(future_two)

        self.window.makeKeyAndOrderFront_(None)

    def _label(self, text: str, x: float, y: float, w: float, h: float, size: float):
        label = NSTextField.alloc().initWithFrame_(NSMakeRect(x, y, w, h))
        label.setStringValue_(text)
        label.setEditable_(False)
        label.setSelectable_(False)
        label.setBordered_(False)
        label.setDrawsBackground_(False)
        label.setFont_(label.font().fontWithSize_(size))
        return label

    def _button(self, title: str, x: float, y: float, w: float, h: float, action: str):
        button = NSButton.alloc().initWithFrame_(NSMakeRect(x, y, w, h))
        button.setTitle_(title)
        button.setTarget_(self)
        button.setAction_(action)
        return button

    @objc.IBAction
    def toggleSupervisor_(self, sender):
        if self.supervisor.running:
            self.supervisor.stop()
            self.supervisor_button.setTitle_("Start ChatGPT Supervisor")
            return

        if self.supervisor.start():
            self.supervisor_button.setTitle_("Stop ChatGPT Supervisor")
            self.updateStatus_("Supervisor started.")

    @objc.IBAction
    def futureFeature_(self, sender):
        self.updateStatus_("Under development — preserved on the future branch.")
        notify_user("AI-cowork", "This feature is under development.")

    def status_from_worker(self, message: str) -> None:
        self.performSelectorOnMainThread_withObject_waitUntilDone_(
            "updateStatus:", message, False
        )

    def updateStatus_(self, message):
        if self.status_label is not None:
            self.status_label.setStringValue_(f"Status: {message}")
        if self.supervisor_button is not None and not self.supervisor.running:
            self.supervisor_button.setTitle_("Start ChatGPT Supervisor")

    def windowWillClose_(self, notification):
        self.supervisor.stop()
        NSApp.terminate_(None)


def run_gui(supervisor: ChatGPTSupervisor) -> None:
    app = NSApplication.sharedApplication()
    app.setActivationPolicy_(NSApplicationActivationPolicyRegular)

    controller = SupervisorWindowController.alloc().initWithSupervisor_(supervisor)
    controller.build()

    # Keep a strong reference for the lifetime of the Cocoa event loop.
    app._ai_cowork_controller = controller
    app.activateIgnoringOtherApps_(True)
    app.run()
