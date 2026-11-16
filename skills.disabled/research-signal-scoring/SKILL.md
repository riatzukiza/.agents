---
description: "Apply structured research workflow: discovery, scoring, scenario modeling, uncertainty quantification, red-teaming, and deliverable writing. Use when analyzing claims, scoring signals, building evidence maps, or researching topics with proper methodology."
---

---
description: "Apply structured research workflow: discovery, scoring, scenario modeling, uncertainty quantification, red-teaming, and deliverable writing. Use when analyzing claims, scoring signals, building evidence maps, or researching topics with proper methodology."
---

# research-signal-scoring

## Purpose

When the user asks to research, analyze, or score signals/claims/evidence, apply a structured workflow that separates discovery, scoring, modeling, red-teaming, and deliverable writing to prevent "pretty chart first, evidence second."

## Trigger

- "research workflow prompts"
- "score the signals"
- "analyze the claims"
- "red-team this analysis"
- "research the current state of [TOPIC]"
- "build an evidence map"

## Workflow

The workflow enforces a strict ordering:

```
1. Research the live state
2. Extract claims and sources
3. Score signals
4. Build scenarios
5. Quantify uncertainty
6. Red-team the result
7. Produce visuals last
```

---

## Phase 1: Research Prompt

Use when you want a clean first-pass evidence map.

```text
You are an analyst. Research the current state of [TOPIC].

Requirements:
- Use credible, primary or near-primary sources first.
- Separate sources by type: official, industry, media, analyst, commentary.
- Do not collapse conflicting claims into one narrative.
- For each source, extract:
  1. claim,
  2. date,
  3. evidence type,
  4. confidence,
  5. what would falsify it.

Output:
- 10 to 20 sourced bullets
- a list of agreement points
- a list of disagreement points
- a list of unknowns
- a short note on which claims are live facts vs interpreted projections

Topic: [INSERT TOPIC]
Time window: [INSERT WINDOW]
```

---

## Phase 2: Signal Prompt

Use to build a scorecard without hand-wavy scoring.

```text
You are building a signal model for [TOPIC].

Requirements:
- Each signal gets explicit weights, not vague scores.
- Weight = (evidence_type_weight) × (source_credibility) × (freshness_decay).
- Show the formula explicitly.
- Separate "what happened" from "what it means".
- Track dependencies: if A depends on B, make that explicit.

Output:
- Signal table: name, weight, formula, dependencies
- Aggregation method: weighted sum? Bayesian update? Scenario-weighted?
- Confidence band: low/medium/high based on independent sources
- Key unknowns that would change the score

Topic: [INSERT TOPIC]
```

---

## Phase 3: Scenario Prompt

Use to build competing future paths.

```text
You are building scenario branches for [TOPIC].

Requirements:
- At least 3 scenarios: baseline, optimistic, pessimistic.
- Each scenario has:
  - trigger conditions
  - probability band (not fake precision)
  - key indicators
  - what would confirm/deny
- Name the assumptions explicitly.
- Include a "wildcard" scenario for low-probability high-impact events.

Output:
- Scenario table: name, probability, triggers, indicators, confirmation signals
- Dependency graph between scenarios
- Time-sensitive: which scenarios become more/less likely over time

Topic: [INSERT TOPIC]
Time horizon: [INSERT HORIZON]
```

---

## Phase 4: Uncertainty Prompt

Use to make uncertainty explicit.

```text
You are quantifying uncertainty for [TOPIC].

Requirements:
- Replace point estimates with ranges.
- Use confidence bands: low/medium/high.
- Distinguish:
  - Aleatory uncertainty (irreducible randomness)
  - Epistemic uncertainty (reducible with more information)
- List what evidence would reduce epistemic uncertainty.
- Explicitly state what you don't know.

Output:
- Uncertainty table: claim, range, confidence, type (aleatory/epistemic)
- Evidence priorities: what to gather next
- Confidence calibration: how often your confidence bands match outcomes

Topic: [INSERT TOPIC]
```

---

## Phase 5: Red-Team Prompt

Use to adversarially test your analysis.

```text
You are red-teaming your own analysis of [TOPIC].

Requirements:
- Steel-man the opposing view.
- Find the weakest link in your evidence chain.
- Identify selection bias: what sources are you not seeing?
- Check for motivated reasoning: what would you want to be true?
- Ask: "If this is wrong, what else must be true?"

Output:
- Strongest counter-argument
- Weakest evidence link
- Evidence you're missing
- Alternative explanations that fit the same data
- What would change your mind

Topic: [INSERT TOPIC]
```

---

## Phase 6: Deliverable Prompt

Use to produce the final output (do visuals last).

```text
You are producing a deliverable for [TOPIC].

Requirements:
- Lead with the strongest evidence, not the prettiest chart.
- Include uncertainty ranges in every claim.
- Cite sources for every factual statement.
- Make assumptions explicit.
- Provide "what would change this" for key conclusions.
- Visuals should clarify, not replace, text.

Output format:
- Executive summary (3 bullets max)
- Key findings with uncertainty
- Evidence map
- Scenarios with probabilities
- Unknowns and evidence priorities
- Caveats and assumptions
- Visual (optional, only if it clarifies)

Topic: [INSERT TOPIC]
```

---

## Example Usage

```
User: "Research the current state of Hormuz shipping disruption"

Agent:
1. Apply Phase 1 (Research) to gather evidence
2. Apply Phase 2 (Signal) to score signals
3. Apply Phase 3 (Scenario) to build branches
4. Apply Phase 4 (Uncertainty) to quantify ranges
5. Apply Phase 5 (Red-Team) to test analysis
6. Apply Phase 6 (Deliverable) to produce output
```

---

## Anti-Patterns to Avoid

- **Pretty chart first**: Visuals before evidence
- **Collapsed narrative**: Forcing conflicting claims into single story
- **Fake precision**: Point estimates without uncertainty ranges
- **Hand-wavy scoring**: "High confidence" without explicit formula
- **Motivated reasoning**: Ignoring evidence that contradicts preferred answer

---

## References

- `research/research-signal-scoring-prompts.md` - Original prompt templates