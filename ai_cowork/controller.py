from __future__ import annotations

import time
from dataclasses import dataclass

from .agents import Agent
from .events import EventLog
from .prompts import CHECKPOINT, CONSULTANT, REVIEW
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

    def checkpoint_once(self) -> dict[str, str]:
        result: dict[str, str] = {}

        for name, agent in (("chatgpt", self.gpt), ("claude", self.claude)):
            runtime = self.store.get(name)
            runtime.state = AgentState.REPORTING
            self.store.save()

            agent.send(CHECKPOINT)
            report = agent.wait_until_stable()
            runtime.last_text = report
            result[name] = report

        self.store.get("chatgpt").state = AgentState.REVIEWING
        self.store.get("claude").state = AgentState.REVIEWING
        self.store.save()

        self.gpt.send(REVIEW.format(report=result["claude"]))
        gpt_review = self.gpt.wait_until_stable()

        self.claude.send(REVIEW.format(report=result["chatgpt"]))
        claude_review = self.claude.wait_until_stable()

        result["gpt_review"] = gpt_review
        result["claude_review"] = claude_review

        if self.deepseek:
            material = (
                "\n\n=== GPT CHECKPOINT ===\n" + result["chatgpt"]
                + "\n\n=== CLAUDE CHECKPOINT ===\n" + result["claude"]
                + "\n\n=== GPT REVIEW ===\n" + gpt_review
                + "\n\n=== CLAUDE REVIEW ===\n" + claude_review
            )
            self.deepseek.send(CONSULTANT.format(material=material))
            result["consultant"] = self.deepseek.wait_until_stable()

        for name in ("chatgpt", "claude"):
            runtime = self.store.get(name)
            runtime.round += 1
            runtime.state = AgentState.WAITING
        self.store.save()
        self.events.emit("checkpoint_complete", round=self.store.get("chatgpt").round)
        return result

    def run(self) -> None:
        self.events.emit("controller_start")
        for _ in range(self.max_rounds):
            self.checkpoint_once()
            time.sleep(self.checkpoint_seconds)
        self.events.emit("controller_stop", reason="max_rounds")
