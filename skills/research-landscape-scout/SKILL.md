---
name: research-landscape-scout
description: "Survey the external landscape for tools, providers, frameworks, papers, docs, or existing implementations, then return a comparison-ready map with recommendations and caveats."
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: landscape-research
  version: 1
---

# Skill: Research Landscape Scout

## Goal
Map what already exists in the external world before designing or implementing something new.

## Use This Skill When
- The user asks for a landscape scan, survey, or ecosystem overview.
- The user says things like:
  - "do some research"
  - "search the web"
  - "what does the landscape look like"
  - "find existing tools/providers/frameworks"
  - "what do the existing benchmarks say?"
- An OpenCode-style request asks for official docs/examples, existing packages, prior art, or external references.

## Do Not Use This Skill When
- The task is mainly about your own repo/docs/history; use `repo-lore-archaeologist`.
- The task is to verify a specific claim or bug; use `verification-lab`.
- The task is mainly benchmark design or interpretation; use `benchmark-intelligence`.

## Inputs
- Research question or decision to inform.
- Scope constraints: ecosystem, language, provider, model family, platform, time window.
- Optional comparison axes: cost, capability, maintenance, licensing, performance, integration difficulty.

## Workflow
1. Define the exact question and the decision it should support.
2. Search broadly, but prefer primary or near-primary sources first:
   - official docs
   - vendor docs/blogs
   - upstream repos
   - maintained examples
   - credible secondary summaries only after primary sources
3. Separate findings into categories:
   - official
   - implementation examples
   - community practice
   - commentary / speculation
4. Build comparison axes explicitly instead of narrating vaguely.
5. Note recency, gaps, and contradictions.
6. End with a recommendation tied to the user's likely decision, not just a pile of links.

## Output
- Short statement of the question being answered.
- Comparison table or bullet matrix of leading options.
- Key tradeoffs and caveats.
- Recommendation with confidence and what could change it.
- Source anchors.

## Strong Hints
- Optimize for decision support, not link hoarding.
- Prefer 3-7 strong options over a giant dump.
- Mark when a source is stale, marketing-heavy, or derivative.
- If the landscape is thin, say so plainly.

## References
- Related analysis skill: `research-signal-scoring`
- Related verification skill: `verification-lab`
- Related synthesis skill: `total-creative-freedom`
