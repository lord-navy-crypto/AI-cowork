import tempfile
import unittest
from pathlib import Path

from ai_cowork.agents import _extract_delta
from ai_cowork.safety import SafetyGuard
from ai_cowork.state import AgentState, RuntimeStore


class CoreTests(unittest.TestCase):
    def test_delta(self):
        before = "hello\nold"
        after = "hello\nold\nnew response"
        self.assertEqual(_extract_delta(before, after), "new response")

    def test_delta_append(self):
        before = "sidebar user prompt old answer"
        after = before + " NEW_REPLY"
        self.assertEqual(_extract_delta(before, after), "NEW_REPLY")

    def test_safety(self):
        guard = SafetyGuard()
        self.assertTrue(guard.check_text("git status").allowed)
        self.assertFalse(guard.check_text("git reset --hard HEAD").allowed)

    def test_state_roundtrip(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "runtime.json"
            store = RuntimeStore(p)
            store.get("chatgpt").state = AgentState.WORKING
            store.get("chatgpt").round = 3
            store.save()

            loaded = RuntimeStore(p)
            loaded.load()
            self.assertEqual(loaded.get("chatgpt").state, AgentState.WORKING)
            self.assertEqual(loaded.get("chatgpt").round, 3)


if __name__ == "__main__":
    unittest.main()
