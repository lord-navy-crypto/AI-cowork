import tempfile
import unittest
from pathlib import Path

from ai_cowork.cursor_supervisor import (
    CursorAction,
    CursorState,
    classify_cursor_text,
    decide_cursor_action,
)
from ai_cowork.cooperation import (
    AgentProtocolState,
    CoordinationSnapshot,
    GitCoordinationMonitor,
    ProtocolGate,
    ProtocolState,
    chatgpt_peer_review_instruction,
    coordination_message_filename,
    cursor_peer_review_instruction,
    evaluate_protocol_state,
    render_coordination_message,
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

    def test_cursor_action_policy_is_conservative(self):
        self.assertEqual(
            decide_cursor_action(CursorState.WAITING, True),
            CursorAction.CLICK_CONTINUE,
        )
        self.assertEqual(
            decide_cursor_action(CursorState.WAITING, False),
            CursorAction.NONE,
        )
        for state in (
            CursorState.UNKNOWN,
            CursorState.LOGIN_REQUIRED,
            CursorState.WORKING,
            CursorState.READY,
            CursorState.FAILED,
        ):
            self.assertEqual(
                decide_cursor_action(state, True),
                CursorAction.NONE,
            )

    def test_protocol_ready_requires_new_review_before_next_cycle(self):
        state = evaluate_protocol_state(
            agent="chatgpt",
            own_head="own123",
            peer_head="peer123",
            own_ts=200,
            peer_ts=100,
            review_ts=150,
            status_ts=250,
            reviewed_peer_head="peer123",
            status_own_head="own123",
        )
        self.assertEqual(state.state, ProtocolState.READY)

    def test_exact_review_head_mismatch_requires_review(self):
        state = evaluate_protocol_state(
            agent="cursor",
            own_head="own123",
            peer_head="peerNEW",
            own_ts=100,
            peer_ts=100,
            review_ts=500,
            status_ts=50,
            reviewed_peer_head="peerOLD",
        )
        self.assertEqual(state.state, ProtocolState.REVIEW_REQUIRED)

    def test_protocol_gate_fails_closed_until_snapshot(self):
        gate = ProtocolGate()
        gate.enable()
        self.assertFalse(gate.allows_own_work("chatgpt"))
        self.assertIn("WAITING_FOR_COORDINATION", gate.block_reason("chatgpt"))

    def test_protocol_gate_allows_only_own_work_allowed(self):
        gate = ProtocolGate()
        gate.enable()
        ready = AgentProtocolState(
            "chatgpt", ProtocolState.READY, "ready", "peer", "own", "r", "s"
        )
        allowed = AgentProtocolState(
            "cursor", ProtocolState.OWN_WORK_ALLOWED, "allowed", "peer", "own", "r", "s"
        )
        gate.update(
            CoordinationSnapshot(
                "chat",
                "cursor",
                "coord",
                "messages/x.md",
                ready,
                allowed,
            )
        )
        self.assertFalse(gate.allows_own_work("chatgpt"))
        self.assertTrue(gate.allows_own_work("cursor"))

    def test_coordination_message_template_records_heads(self):
        text = render_coordination_message(
            "chatgpt",
            "review",
            summary="Reviewed peer changes.",
            peer_head="abc123",
            own_head="def456",
            status="READY",
            next_action="Continue on agent/chatgpt.",
        )
        self.assertIn("- Peer head reviewed: abc123", text)
        self.assertIn("- Own head: def456", text)
        self.assertIn("## Next action", text)

    def test_coordination_message_filename(self):
        path = coordination_message_filename("cursor", "status")
        self.assertTrue(path.startswith("messages/"))
        self.assertTrue(path.endswith("-cursor-status.md"))


if __name__ == "__main__":
    unittest.main()
