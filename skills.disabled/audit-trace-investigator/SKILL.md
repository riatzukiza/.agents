---
name: audit-trace-investigator
description: "Investigate observability gaps, process drift, missing evidence, or source-to-sink failures by tracing how data, decisions, or work items move through a system and where auditability breaks down."
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: auditing
  version: 1
---

# Skill: Audit Trace Investigator

## Goal
Trace the path from source to sink and explain where evidence, observability, or process integrity breaks.

## Use This Skill When
- The user says things like:
  - "investigate"
  - "audit"
  - "trace"
  - "find out why"
  - "I want to see the actual feeds"
  - "observability"
  - "inspect the sources"
- A workflow, dashboard, pipeline, or process is opaque or lying by omission.
- You need to map where truth is lost between generation, routing, storage, and display.

## Do Not Use This Skill When
- The task is merely broad external research; use `research-landscape-scout`.
- The task is only a binary truth check; use `verification-lab`.
- The task is only repo-history excavation; dispatch the `repo-lore-archaeologist` subagent.

## Inputs
- The failing or suspicious surface.
- Expected source-to-sink path.
- Relevant logs, dashboards, codepaths, schemas, and process docs.

## Workflow
1. Define the claim the system is making.
2. Identify the true upstream sources.
3. Trace each transformation step to the user-facing surface.
4. Mark where data is dropped, relabeled, delayed, hidden, or synthesized.
5. Separate:
   - code bug
   - instrumentation gap
   - process gap
   - UX omission
   - stale or misleading documentation
6. Return the shortest repair order.

## Output
- Trace map: source → transforms → sink.
- Breakpoints and evidence gaps.
- Which claims are trustworthy vs not.
- Instrumentation or process changes needed.
- Suggested validation checks after repair.

## Strong Hints
- Observability problems often masquerade as product problems.
- The dashboard is not the source of truth.
- Preserve a chain of receipts whenever possible.
- Repair the highest-leverage breakpoint first.

## References
- Related verification skill: `verification-lab`
- Related benchmark skill: `benchmark-intelligence`
- Related methodology skill: `research-signal-scoring`
