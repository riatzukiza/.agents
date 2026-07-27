# Perplexity Adapter

## Scope

Perplexity can mean several materially different environments: Ask/Research, asset creation, Computer's cloud sandbox and connectors, Personal Computer on macOS, or an older ephemeral Debian task sandbox.

Do not infer one from the product name. Run `environment-classifier` first.

## Discovery

1. Inspect the visible tool list, connected apps, writable files, and task instructions.
2. Determine whether there is a shell, repository checkout, GitHub connector, browser, code runner, persistent project storage, or only research output.
3. Determine whether the task container resets and what artifacts survive.
4. Search provided project files for `AGENTS.md`, `PROCESS.md`, `STYLE.md`, and relevant skills.
5. Record hard limitations before promising repository mutation or later delivery.

## Process projection

### Ask or Research

- Treat the environment as research/chat-only unless tools prove otherwise.
- Produce cited findings, concrete patches, plans, or setup instructions.
- Do not claim files, branches, commands, tests, or installs were changed.
- Separate external evidence from repository interpretation.

### Computer cloud task

- Treat the sandbox and connectors as separate capabilities.
- Use connectors for authoritative remote systems when available.
- Keep secrets in approved credential surfaces; never echo them into files or receipts.
- Record which actions occurred through connectors versus sandbox code.
- Export or persist repository artifacts through the supported destination before task completion.

### Personal Computer

- Local file access does not imply unrestricted shell, network, Git, or application permissions.
- Probe each capability and respect approval boundaries.
- Prefer the repository's own commands and skills when a real checkout is available.

### Legacy ephemeral Debian sandbox

When the task supplies `SETUP.sh`, prefer it over ad-hoc installation. Verify the installed toolchain rather than assuming it survived a reset.

If the environment is ephemeral, package changed files, setup instructions, and process records into a durable artifact before completion. The older known protocol used a ZIP containing `SETUP.sh`, shell profile, `AGENTS.md`, and changed files; follow the active project protocol when it differs.

## Tool bootstrap

Perplexity must not require eta-mu or Muse.

- File-only: create or update the declared process artifacts and export them.
- GitHub connector: use branches and PRs; preserve append-only records through API writes.
- Shell checkout: use `process-bootstrap`, then install eta-mu/Rheos only when requested and supported.
- MCP or app connector: treat it as an adapter; the repository remains the source of truth.

## Persistence rule

State the persistence class in the handoff:

```text
persistent-local | persistent-project | session-only | reset-prone | connector-only | unknown
```

Never tell the user work will continue after the task unless the product has an explicit scheduled/background mechanism and it was actually configured.
