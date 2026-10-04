---
name: model-routing
description: "Route tasks by role across the free fleet, GPT-6.1 Sol, and Kimi coding models; check current provider availability and stop with informative errors on failures."
---

# Skill: Model Routing

## Goal
Pick the agent whose role fits the task, respecting explicit model bindings and checking current provider availability.

## Use This Skill When
- You must choose which agent or model handles a task.
- You are spawning subagents and need a task-type → agent mapping.
- A provider/model call fails and you need to report the blocker and recovery evidence.

## Do Not Use This Skill When
- The agent/model is already fixed by the user or harness.
- The task is trivial and the current model is adequate.

## Routing Table

| Task type | Agent | Model | Why |
|---|---|---|---|
| Read-only exploration, long-context surveys | explore | opencode/nemotron-3.5-lightning-free | Scout is disabled; use the enabled exploration agent |
| Fast general implementation | sprinter | opencode/deepseek-v4-flash-free | Best free agentic coding (Terminal-Bench 82.7); weak factual recall — gate with tests |
| Image/audio/video analysis | visionary | opencode/mimo-v2.5-free | Multimodal; coding is its weak spot |
| UI/frontend/CI chores | frontend | opencode/hy3-free | Slow first token; not for deep agentic work |
| Multilingual/i18n, short-context coding | polyglot | opencode/laguna-s-2.1-free | Last place on long-context retrieval — one file at a time |
| High-volume mechanical transforms | flock | opencode/nemotron-3.5-lightning-free | ~670 tok/s; no design decisions |
| Default production implementation | wright | openai/gpt-6.1-sol | Features, bugfixes, refactors; verified implementation |
| Hardest agentic/design/terminal problems | architect | openai/gpt-6.1-sol | Escalation role; architecture and evidence-driven root-cause analysis |
| Classify/summarize/extract at volume | triage | openai/gpt-6.1-sol | One bounded item; read and route, no code edits or builds |
| Precision high-stakes coding, frontend polish | surgeon | kimi-for-coding/k3 | SWE-bench Verified 93.4; slow |
| Long-haul grinding, multi-file migrations | mule | kimi-for-coding/k3-256k | MUST gate completion on real test runs |
| MCP/tool-heavy loops | looper | opencode/kimi-k2.7-code | Needs opencode billing; cap iterations |

## Provider Availability and Failure Handling

- Availability is session-specific; verify the configured provider/model with a bounded live invocation rather than relying on dated auth or billing reports.
- Wright, Architect, and Triage are bound to `openai/gpt-6.1-sol`. Their distinct roles are prompt contracts, not model performance or price tiers.
- On failure, report the agent, configured provider/model, observed error, and any evidence-backed recovery step, then stop. Never substitute another provider/model automatically.
- If the observed error specifically indicates OpenAI OAuth authentication failure, report `opencode auth login -p openai` as the recovery action. Do not assume every failure is an authentication problem.

## Model-Family Cheat Sheet

- **GPT-6.1 Sol roles**: Wright implements; Architect analyzes system design and root causes; Triage classifies, summarizes, and extracts bounded items. No benchmark, cost, speed, or recall ranking is implied by these bindings.
- **Kimi**: K3 = frontier coding/frontend; K2.7-code = MCP loops but verbose; kimi models barrel past wrong turns → always add test gates.
- **Free fleet**: strengths/weaknesses per the routing table above — explore handles codebase discovery, sprinter owns speed, visionary owns multimodal, the rest are narrow. Scout is disabled and must not be dispatched.

## Rules

1. Keep Triage inputs bounded by its routing role; do not route long-context retrieval to polyglot.
2. Never route design work to flock or frontend.
3. Always attach a verification gate to sprinter, mule, looper, and anything gemini-flash-like.
4. Respect explicit model bindings; choose by task scope and verified evidence rather than assumed price or performance tiers.

## References

- Agent files live in `~/.config/opencode/agents/`.
- `~/.config/opencode/opencode.jsonc` is daemon-generated — do not edit it; agent model bindings live in the agent files.
