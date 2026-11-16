---
name: whitespace-tolerant-require-audits
description: Audit namespace requires and usages with whitespace-tolerant patterns before deleting or deprecating code.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: clojure-require-audit
  project: gates-of-truth
  discoverable-by:
    - opencode
    - eta-mu
    - claude
  version: 1
---

# Skill: Whitespace-Tolerant Require Audits

## Goal
Accurately count and locate all callers of a Clojure namespace before deleting, renaming, or deprecating it.

## Use This Skill When
- You are about to delete, rename, or deprecate a namespace.
- A caller-count assertion will drive a go/no-go decision.
- Requires may use aligned-column formatting (`[ns        :as ...]`).

## Do Not Use This Skill When
- The project is not Clojure/ClojureScript.
- You only need a rough idea of usage (a simple grep is enough).
- You have already deleted the namespace and only need to find breakage.

## Steps
1. Identify every public symbol the namespace exposes.
2. Search for `:require`/`:use`/`require` references with whitespace-tolerant patterns: `\[ns +:as`, `\[ns +:refer`, `ns +:as`, etc.
3. Search for symbol usages that may bypass the require (fully qualified calls, dynamic require, runtime resolve).
4. Run a compile check / full test suite as the ground truth after the search.
5. Only report caller counts after both search and compile/test pass.
6. Perform the deletion/rename and run a safety sweep to catch stragglers.

## Anti-patterns
- Asserting a caller count from a single narrow grep pattern.
- Trusting `\[ns :as` with one space in aligned-column code.
- Treating grep as ground truth; the compiler/full suite is ground truth.
- Deleting before running the safety sweep.

## Output
- A confident, evidence-based caller list.
- A safe namespace deletion or migration with no hidden callers.
