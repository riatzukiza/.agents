# Codex Adapter

## Scope

Codex may run locally in a terminal or IDE, in the Codex app, or in an isolated cloud sandbox. Filesystem, network, approval, persistence, Git, and background capabilities vary by surface and task.

Run `environment-classifier` before capability-dependent work.

## Instruction discovery

1. Read the sandbox and permission instructions supplied by the harness.
2. Read applicable `AGENTS.override.md` and `AGENTS.md` files from the global/project chain.
3. Read [`PRINCIPLE.edn`](PRINCIPLE.edn), [`PROCESS.md`](PROCESS.md), and [`STYLE.md`](STYLE.md) when present.
4. Discover project and global skills; load only those whose trigger matches the task.
5. Inspect the repository and current worktree before planning edits.

Keep `AGENTS.md` navigational. Put deep process, architecture, and harness material in linked files and skills.

## Process projection

### Local Codex

- Verify the actual checkout, branch, dirt, network, and command availability.
- Use path-scoped changes and checks.
- Preserve concurrent work.
- Append process records through the repository's scripts or files.
- Do not create commits, tags, or pushes unless the task or Fork Tax requires them.

### Cloud Codex task

- Treat the provided worktree and sandbox policy as the capability boundary.
- Install dependencies only when network and policy allow it.
- Leave a reviewable diff and exact verification record.
- Do not assume background continuation beyond the task.
- Materialize durable process artifacts in the repository rather than relying on task memory.

### Review-only Codex

- Separate findings from fixes.
- Cite exact paths/lines or patches.
- Do not mutate unless the review task explicitly permits it.

## Skills

Codex skills are progressive disclosure:

- metadata advertises the skill,
- `SKILL.md` supplies the procedure,
- scripts and references are loaded only when needed.

The canonical source in this ecosystem is `~/.agents/skills`. Project-local `.agents/skills` may specialize or override within project scope. Avoid copying divergent skill trees.

## Completion

Before claiming completion:

- inspect the diff,
- run the smallest relevant checks available,
- record blocked or skipped checks,
- append Receipt River and Session Mycology state for substantive work,
- and leave Git state truthful.
