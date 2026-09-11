---
name: threat-dataset-research
description: "Research datasets, threat models, harmful-task corpora, multilingual eval inputs, and safety-evaluation sources with explicit attention to licensing, contamination risk, coverage gaps, and mitigation relevance."
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: dataset-research
  version: 1
---

# Skill: Threat Dataset Research

## Goal
Find and evaluate datasets and corpora for safety, threat-modeling, refusal, jailbreak, or multilingual harmful-task research without losing track of licensing and misuse risk.

## Use This Skill When
- The user asks about:
  - datasets
  - threat models
  - harmful-task prompts
  - jailbreak corpora
  - Hugging Face sources
  - multilingual safety-eval inputs
  - mitigation strategy data
- A project needs seed data for evaluation, analysis, or controlled research.

## Do Not Use This Skill When
- The request is for broad ecosystem mapping; use `research-landscape-scout`.
- The task is benchmark interpretation rather than corpus discovery; use `benchmark-intelligence`.
- The task would cross into generating harmful instructions; refuse or redirect to safer evaluation framing.

## Inputs
- Research purpose.
- Domain or harm class.
- Desired languages, modalities, and licenses.
- Constraints on safety, publication, and storage.

## Workflow
1. Define the exact evaluation or research purpose.
2. Search for candidate datasets and papers.
3. For each candidate, record:
   - source
   - license
   - scope
   - language coverage
   - format
   - contamination / duplication risk
   - likely relevance
4. Distinguish:
   - directly usable
   - usable with cleaning
   - reference only
   - unsafe / unsuitable
5. Highlight missing coverage and what must be generated or collected.

## Output
- Dataset matrix.
- Suitability notes.
- Risks and caveats.
- Recommended acquisition order.
- Coverage gaps.

## Strong Hints
- Optimize for evaluation quality, not maximal edginess.
- License and provenance matter.
- Separate corpus discovery from corpus generation.
- Always note if the available data is biased toward one language, provider, or threat style.

## References
- Related analysis skill: `research-signal-scoring`
- Related benchmark skill: `benchmark-intelligence`
- Related repo excavation subagent: `repo-lore-archaeologist`
