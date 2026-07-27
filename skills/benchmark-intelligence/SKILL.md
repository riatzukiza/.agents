---
name: benchmark-intelligence
description: "Read, compare, design, or critique benchmarks with explicit metrics, fairness constraints, confounders, and result interpretation so 'get numbers' becomes a usable benchmark plan or benchmark reading."
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: benchmarking
  version: 1
---

# Skill: Benchmark Intelligence

## Goal
Convert vague performance curiosity into disciplined benchmark work.

## Use This Skill When
- The user asks for:
  - benchmarks
  - numbers
  - evaluation results
  - success rates
  - performance comparisons
  - benchmark design or interpretation
- You need to compare models, systems, providers, prompts, or workflows fairly.
- You need to map existing benchmark codepaths or result formats.

## Do Not Use This Skill When
- The task is only external prior-art research; use `research-landscape-scout`.
- The task is only factual verification of one claim; use `verification-lab`.
- The task is mainly threat-dataset sourcing; use `threat-dataset-research`.

## Inputs
- System or model under test.
- Comparison set.
- Metrics of interest.
- Constraints: hardware, budget, time, network stability, provider quotas.

## Workflow
1. Determine whether the ask is:
   - read existing benchmarks
   - map benchmark implementation
   - design a new benchmark
   - interpret suspicious results
2. Define fairness constraints explicitly.
3. Identify metrics and their failure modes.
4. Note confounders:
   - caching
   - retries
   - hardware
   - prompt differences
   - quota / rate limits
   - data contamination
5. Produce either:
   - a benchmark map,
   - a run plan,
   - or an interpretation memo.

## Output
- Benchmark question.
- Metrics and why they matter.
- Fairness and confounders.
- Existing harnesses/codepaths if present.
- Recommended next run or interpretation.

## Strong Hints
- Benchmarking without fairness notes is noise.
- Distinguish throughput, quality, reliability, and cost.
- If a result smells wrong, prioritize explanation before expansion.
- Make caching and retry policy explicit.

## References
- Related research skill: `research-landscape-scout`
- Related structured analysis skill: `research-signal-scoring`
- Related audit skill: `audit-trace-investigator`
