---
name: environment-classifier
description: Classify the active agent harness, filesystem, shell, network, repository, persistence, connector, approval, and background capabilities before selecting a process workflow.
license: LGPL-3.0-or-later
metadata:
  audience: agents
  workflow: environment-orientation
  version: "1"
---

# Skill: Environment Classifier

## Goal

Determine what the active harness can actually inspect, mutate, execute, persist, and verify, then map the universal process onto those capabilities without pretending missing tools exist.

## Use This Skill When

- Starting substantive work in an unfamiliar harness or sandbox.
- The task crosses local files, connectors, GitHub, web research, code execution, or generated artifacts.
- Persistence, network, shell, repository mutation, or background execution is uncertain.
- Instructions mention ChatGPT, Claude, Codex, OpenCode, Perplexity, MCP, connectors, or a web sandbox.
- A prior agent confused a connector with a local checkout or claimed an unavailable check ran.

## Do Not Use This Skill When

- The request is trivial and capability-independent.
- The harness has already supplied a fresh, explicit capability classification for this task.

## Rules

- Read the harness's declared tool/permission instructions before probing.
- Prefer declared evidence over speculative probes.
- Never probe by exposing secrets, contacting unrelated services, or mutating user data.
- Use temporary, reversible probes only when needed and permitted.
- `unknown` is a valid classification.
- Capabilities are session-scoped observations, not permanent facts about a product.
- Record the actual execution boundary: local shell, sandbox, connector API, browser, or research-only.

## Dimensions

Classify each as `yes`, `no`, `restricted`, or `unknown`, with evidence:

- repository read
- repository write
- local filesystem read
- local filesystem write
- shell/process execution
- language/runtime execution
- network/web access
- package installation
- browser automation
- connectors/MCP/apps
- Git commit/branch/tag/push
- persistent home or project storage
- task/session lifetime
- background or scheduled execution
- approval/permission boundary
- secret handling surface
- artifact export/download
- concurrent-agent/worktree isolation

## Safe workflow

1. **Read declarations**
   - tool list
   - sandbox/permission prompt
   - connected sources
   - repository instructions
   - task lifetime and persistence notes
2. **Identify the repository surface**
   - real checkout
   - mounted files
   - uploaded archive
   - connector reference
   - remote repository API
   - none
3. **Probe only unresolved material capabilities**
   - `pwd`, `git rev-parse`, runtime versions, or a temp-file write when shell access is allowed
   - repository metadata/read calls when a connector exists
   - no network probe unless the task needs network
4. **Classify**
   - `local-full`
   - `local-restricted`
   - `cloud-sandbox`
   - `connector-only`
   - `research-only`
   - `mixed`
   - `unknown`
5. **Project the process**
   - choose the strongest safe Receipt River, mycology, board, verification, and handoff implementation available
6. **Record**
   - include limitations in the work plan and final handoff
   - for substantive repository work, append an environment observation receipt when a writable process ledger exists

## Output shape

```edn
{:observed-at "<ISO-8601>"
 :harness "<name or unknown>"
 :class :connector-only
 :capabilities
 {:repo-read :yes
  :repo-write :yes
  :filesystem :no
  :shell :no
  :network :restricted
  :persistence :remote-repository
  :background :no}
 :evidence ["GitHub connector exposes fetch/update/PR actions"
            "No local checkout or shell tool is available"]
 :process-projection
 {:receipts :github-append
  :verification :repository-readback
  :handoff :branch-and-pr}
 :unknowns []}
```

The map may be returned in the response or stored as an append-only observation under `.ημ/environment/`. Do not treat a generated `current.edn` view as durable authority after the session that produced it.

## References

- `PROCESS.md`
- `CHATGPT.md`
- `CLAUDE.md`
- `CODEX.md`
- `OPENCODE.md`
- `PERPLEXITY.md`
- `process-bootstrap`
