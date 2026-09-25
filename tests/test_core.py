import tempfile
import unittest
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main()
