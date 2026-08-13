---
name: model-routing
description: "Route tasks to the right agent/model across the free fleet, GPT-5.6 tiers, and Kimi coding models; includes verified provider availability and fallback chains."
---

# Skill: Model Routing

## Goal
Pick the cheapest agent/model that can actually do the task, using verified provider availability and hard routing rules.

## Use This Skill When
- You must choose which agent or model handles a task.
- You are spawning subagents and need a task-type → agent mapping.
- A paid-tier model call fails and you need a fallback.

## Do Not Use This Skill When
- The agent/model is already fixed by the user or harness.
- The task is trivial and the current model is adequate.

## Routing Table

| Task type | Agent | Model | Why |
|---|---|---|---|
| Read-only exploration, long-context surveys | scout | opencode/nemotron-3-ultra-free | 400+ tok/s, RULER 94.7@1M, low hallucination |
| Fast general implementation | sprinter | opencode/deepseek-v4-flash-free | Best free agentic coding (Terminal-Bench 82.7); weak factual recall — gate with tests |
| Image/audio/video analysis | visionary | opencode/mimo-v2.5-free | Multimodal; coding is its weak spot |
| UI/frontend/CI chores | frontend | opencode/hy3-free | Slow first token; not for deep agentic work |
| Multilingual/i18n, short-context coding | polyglot | opencode/laguna-s-2.1-free | Last place on long-context retrieval — one file at a time |
| High-volume mechanical transforms | flock | opencode/nemotron-3.5-lightning-free | ~670 tok/s; no design decisions |
| Default production implementation | wright | openai/gpt-5.6-terra | Everyday default (openai OAuth) |
| Hardest agentic/design/terminal problems | architect | openai/gpt-5.6-sol | Escalation tier (openai OAuth) |
| Classify/summarize/extract at volume | triage | openai/gpt-5.6-luna | Short inputs only; long-context recall collapses (openai OAuth) |
| Precision high-stakes coding, frontend polish | surgeon | kimi-for-coding/k3 | SWE-bench Verified 93.4; slow |
| Long-haul grinding, multi-file migrations | mule | kimi-for-coding/k3-256k | MUST gate completion on real test runs |
| MCP/tool-heavy loops | looper | opencode/kimi-k2.7-code | Needs opencode billing; cap iterations |

## Provider Availability (verified 2026-08-13)

- opencode **free** models: **WORK**.
- openai (gpt-5.6-* via OAuth): configured, but token refresh **fails with 401** (stored token expired 2026-03-20) — fix with `opencode auth login -p openai`.
- opencode **paid** (gpt-5.6-*, kimi-k2.7-code): **FAIL** with "No payment method".
- kimi-for-coding (k3, k3-256k): **WORKS**.
- aihubmix, vivgrid, requesty, openrouter, zai: all **failed** today (auth/billing/timeout).

**Fallback rule:** when a paid-tier agent fails, fall back to kimi-for-coding/k3 (quality) or the matching free-fleet agent (cost).

## Model-Family Cheat Sheet

- **GPT-5.6 tiers**: Sol = flagship agentic; Terra = everyday default; Luna = high-volume short-context (recall cliff on long inputs).
- **Kimi**: K3 = frontier coding/frontend; K2.7-code = MCP loops but verbose; kimi models barrel past wrong turns → always add test gates.
- **Free fleet**: strengths/weaknesses per the routing table above — scout owns long context, sprinter owns speed, visionary owns multimodal, the rest are narrow.

## Rules

1. Never route long-context retrieval to luna or polyglot.
2. Never route design work to flock or frontend.
3. Always attach a verification gate to sprinter, mule, looper, and anything gemini-flash-like.
4. Prefer the cheapest tier that can do the task.

## References

- Agent files live in `~/.config/opencode/agents/`.
- `~/.config/opencode/opencode.jsonc` is daemon-generated — do not edit it; agent model bindings live in the agent files.
