# OpenCode Adapter

## Scope

OpenCode is the native consumer of much of this skill corpus, but the process must remain valid without OpenCode, eta-mu, or Muse.

Run `environment-classifier` before capability-dependent work.

## Discovery

OpenCode can discover skills from project and global `.agents/skills` locations and load them on demand. Prefer that native path over duplicating skill bodies into the system prompt.

1. Read applicable `AGENTS.md`.
2. Read the universal process/style maps.
3. Inspect the advertised skill metadata.
4. Load only the smallest relevant skill set.
5. Check permissions before assuming edit, shell, task, web, external-directory, or skill access.

Do not run `/init` over an existing curated `AGENTS.md` without preserving and reviewing the existing contract.

## Process projection

### Plain OpenCode

- Use built-in file, shell, task, web, and LSP tools only when allowed.
- Work from the declared board/task source.
- Append receipts and mycology records.
- Use subagents for bounded inquiry/review, not to blur acceptance authority.
- Preserve child-session provenance when results feed a decision.

### OpenCode with eta-mu

- eta-mu is the control-plane/bootstrap entry point, not the source of every semantic law.
- Rheos owns board transition operations.
- `.ημ/` is the preferred process-data home.
- event-ledger owns event envelope/causality/replay laws where adopted.
- Use `eta-mu-kanban` for board operations and migration guidance.

### OpenCode with Muse

- Edit host-agnostic resources/config first.
- Rebuild generated OpenCode plugins, agents, permissions, and config through Muse.
- Treat `.opencode/dist`, generated shims, and generated settings as projections.
- Do not patch generated output as the durable source unless no source declaration exists; record that exception.

## Permissions

Skill visibility and tool permission are separate. A skill being discoverable does not prove its required tools are allowed.

When a skill cannot execute fully:

- preserve its invariants,
- choose the strongest available fallback,
- record the missing capability,
- and bootstrap only when requested.

## Completion

A relevant red check blocks a completion claim. A subagent review is evidence, not automatic acceptance. Record exact commands, results, and remaining limits.
