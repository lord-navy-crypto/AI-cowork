import tempfile
import unittest
from pathlib import Path

from ai_cowork.cursor_supervisor import CursorState, classify_cursor_text
from ai_cowork.cooperation import (
    CoordinationSnapshot,
    GitCoordinationMonitor,
    ProtocolState,
    chatgpt_peer_review_instruction,
    cursor_peer_review_instruction,
    evaluate_protocol_state,
)
from ai_cowork.web_runtime import (
    SettingsStore,
    WebSettings,
    context_limit_detected,
    validate_chatgpt_url,
    validate_cursor_url,
)


class CoreTests(unittest.TestCase):
    def test_url_validation(self):
        self.assertTrue(validate_chatgpt_url("https://chatgpt.com/c/example"))
        self.assertFalse(validate_chatgpt_url("https://example.com/c/example"))
        self.assertTrue(validate_cursor_url("https://cursor.com/agents/example"))
        self.assertFalse(validate_cursor_url("http://cursor.com/agents/example"))

    def test_context_limit_detection(self):
        self.assertTrue(
            context_limit_detected(
                "This conversation has reached the maximum context length."
            )
        )
        self.assertTrue(context_limit_detected("上下文窗口已满，请开始新对话。"))
        self.assertFalse(context_limit_detected("Finished the implementation and tests."))

    def test_settings_roundtrip(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "settings.json"
            store = SettingsStore(path)
            original = WebSettings(
                chatgpt_url="https://chatgpt.com/c/test",
                cursor_url="https://cursor.com/agents/test",
                headless=True,
            )
            store.save(original)
            loaded = store.load()
            self.assertEqual(loaded.chatgpt_url, original.chatgpt_url)
            self.assertEqual(loaded.cursor_url, original.cursor_url)
            self.assertTrue(loaded.headless)

    def test_settings_clamp_unsafe_timings(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "settings.json"
            path.write_text(
                '{"idle_confirm_seconds": 0, "poll_interval_seconds": -10}',
                encoding="utf-8",
            )
            loaded = SettingsStore(path).load()
            self.assertEqual(loaded.idle_confirm_seconds, 1.5)
            self.assertEqual(loaded.poll_interval_seconds, 0.5)

    def test_cursor_state_classifier(self):
        self.assertEqual(
            classify_cursor_text("Agent is working on your task").state,
            CursorState.WORKING,
        )
        self.assertEqual(
            classify_cursor_text("Task completed. Ready for review.").state,
            CursorState.READY,
        )
        self.assertEqual(
            classify_cursor_text("Agent failed because something went wrong").state,
            CursorState.FAILED,
        )
        self.assertEqual(
            classify_cursor_text("Please log in to continue").state,
            CursorState.LOGIN_REQUIRED,
        )

    def test_cooperation_change_description(self):
        monitor = GitCoordinationMonitor(".")
        before = CoordinationSnapshot("aaa", "bbb", "ccc", "messages/a.md")
        after = CoordinationSnapshot("aaa", "ddd", "eee", "messages/b.md")
        description = monitor._describe_change(before, after)
        self.assertIn("Cursor branch updated", description)
        self.assertIn("coordination updated", description)

    def test_peer_review_instructions_preserve_branch_ownership(self):
        self.assertIn("agent/cursor", chatgpt_peer_review_instruction())
        self.assertIn("agent/chatgpt", cursor_peer_review_instruction())
        self.assertIn("Never write main", chatgpt_peer_review_instruction())
        self.assertIn("Never write main", cursor_peer_review_instruction())

    def test_protocol_requires_review_after_peer_update(self):
        state = evaluate_protocol_state(
            agent="chatgpt",
            own_head="own",
            peer_head="peer",
            own_ts=100,
            peer_ts=200,
            review_ts=150,
            status_ts=100,
        )
        self.assertEqual(state.state, ProtocolState.REVIEW_REQUIRED)

    def test_protocol_requires_status_after_own_work(self):
        state = evaluate_protocol_state(
            agent="cursor",
            own_head="own",
            peer_head="peer",
            own_ts=300,
            peer_ts=100,
            review_ts=200,
            status_ts=250,
        )
        self.assertEqual(state.state, ProtocolState.STATUS_REQUIRED)

    def test_protocol_allows_work_after_fresh_review(self):
        state = evaluate_protocol_state(
            agent="chatgpt",
            own_head="own",
            peer_head="peer",
            own_ts=100,
            peer_ts=200,
            review_ts=250,
            status_ts=100,
        )
        self.assertEqual(state.state, ProtocolState.OWN_WORK_ALLOWED)


if __name__ == "__main__":
    unittest.main()
