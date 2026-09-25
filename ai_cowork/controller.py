from __future__ import annotations

from dataclasses import dataclass

from .agents import Agent
from .events import EventLog
from .prompts import (
    CONSULTANT,
    DEVELOPER_A,
    DEVELOPER_B,
    CONTINUE_WORK,
    REVIEW,
    START_WORK,
)
from .state import AgentState, RuntimeStore


@dataclass
class Controller:
    gpt: Agent
    claude: Agent
    deepseek: Agent | None
    checkpoint_seconds: int
    max_rounds: int
    store: RuntimeStore
    events: EventLog
    gpt_objective: str
    claude_objective: str

    def _capture_work_reports(self) -> dict[str, str]:
        """Wait for both independent work rounds to finish, without hard timeout."""
        result: dict[str, str] = {}

        # Both prompts have already been sent, so waiting sequentially here does
        # not serialize the actual engineering work. Claude and GPT can both be
        # thinking/tool-calling while the controller waits on one of them.
        for name, agent in (("chatgpt", self.gpt), ("claude", self.claude)):
            runtime = self.store.get(name)
            runtime.state = AgentState.REPORTING
            self.store.save()

            report = agent.wait_until_stable(timeout=None)
            runtime.last_text = report
            result[name] = report

        return result

    def _cross_review(self, result: dict[str, str]) -> dict[str, str]:
        self.store.get("chatgpt").state = AgentState.REVIEWING
        self.store.get("claude").state = AgentState.REVIEWING
        self.store.save()

        # Start both reviews before waiting, so neither model is idle just
        # because the controller happens to read the other one first.
        self.gpt.send(REVIEW.format(report=result["claude"]))
        self.claude.send(REVIEW.format(report=result["chatgpt"]))

        gpt_review = self.gpt.wait_until_stable(timeout=None)
        claude_review = self.claude.wait_until_stable(timeout=None)

        result["gpt_review"] = gpt_review
        result["claude_review"] = claude_review

        consultant = "No consultant report for this round."
        if self.deepseek:
            material = (
                "\n\n=== GPT CHECKPOINT ===\n" + result["chatgpt"]
                + "\n\n=== CLAUDE CHECKPOINT ===\n" + result["claude"]
                + "\n\n=== GPT REVIEW ===\n" + gpt_review
                + "\n\n=== CLAUDE REVIEW ===\n" + claude_review
            )
            self.deepseek.send(CONSULTANT.format(material=material))
            consultant = self.deepseek.wait_until_stable(timeout=None)
            result["consultant"] = consultant

        return result

    def _start_round(self, first: bool, previous: dict[str, str] | None = None) -> None:
        if first:
            gpt_prompt = START_WORK.format(role=DEVELOPER_A, objective=self.gpt_objective)
            claude_prompt = START_WORK.format(role=DEVELOPER_B, objective=self.claude_objective)
        else:
            assert previous is not None
            consultant = previous.get("consultant", "No consultant report for this round.")
            gpt_prompt = CONTINUE_WORK.format(
                objective=self.gpt_objective,
                review=previous["claude_review"],
                consultant=consultant,
            )
            claude_prompt = CONTINUE_WORK.format(
                objective=self.claude_objective,
                review=previous["gpt_review"],
                consultant=consultant,
            )

        # Send both before waiting: they are independent developers, not a
        # request/response relay.
        self.gpt.send(gpt_prompt)
        self.claude.send(claude_prompt)

    def run(self) -> None:
        self.events.emit("controller_start")
        previous: dict[str, str] | None = None

        for round_index in range(self.max_rounds):
            self._start_round(first=round_index == 0, previous=previous)
            reports = self._capture_work_reports()
            previous = self._cross_review(reports)

            for name in ("chatgpt", "claude"):
                runtime = self.store.get(name)
                runtime.round += 1
                runtime.state = AgentState.WAITING
            self.store.save()
            self.events.emit(
                "checkpoint_complete",
                round=self.store.get("chatgpt").round,
            )

        self.events.emit("controller_stop", reason="max_rounds")
