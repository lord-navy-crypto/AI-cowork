import unittest

from ai_cowork.agents import _extract_delta
from ai_cowork.supervisor import context_limit_detected


class CoreTests(unittest.TestCase):
    def test_delta_append(self):
        before = "sidebar user prompt old answer"
        after = before + " NEW_REPLY"
        self.assertEqual(_extract_delta(before, after), "NEW_REPLY")

    def test_context_limit_english(self):
        self.assertTrue(
            context_limit_detected(
                "This conversation has reached the maximum context length."
            )
        )

    def test_context_limit_chinese(self):
        self.assertTrue(context_limit_detected("上下文窗口已满，请开始新对话。"))

    def test_normal_work_report_is_not_context_limit(self):
        self.assertFalse(
            context_limit_detected(
                "Completed the current implementation and tests. Ready for the next task."
            )
        )


if __name__ == "__main__":
    unittest.main()
