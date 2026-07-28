# Claude Adapter

## Scope

This file adapts the universal process to Claude Code and Claude environments. Capabilities vary between terminal, IDE, web, connectors, MCP, and permission modes.

Run `environment-classifier` before capability-dependent work.

## Instruction discovery

1. Read the active user and project instructions.
2. Read the nearest applicable `CLAUDE.md` and `AGENTS.md` files.
3. Read [`PRINCIPLE.edn`](PRINCIPLE.edn), [`PROCESS.md`](PROCESS.md), and [`STYLE.md`](STYLE.md) when this repository is available.
4. Load the smallest relevant skill from project-local or global skill directories.
5. Treat hooks, subagents, plugins, and MCP servers as optional adapters, not constitutional authority.

A project may reference this file from its own `CLAUDE.md` rather than copying the entire process.

## Process projection

### Claude Code with a local checkout

- Inspect permission mode, writable roots, network access, and available commands.
- Work from the repository's declared task source.
- Use project commands and checks.
- Append receipts and mycology records through scripts when available, otherwise through safe file edits.
- Preserve unrelated concurrent changes.
- Do not skip permission boundaries to make the workflow appear smoother.

### Claude with connectors or MCP

- Identify which system owns each fact or mutation.
- Record connector/MCP actions as external effects with their actual source.
- Do not treat connector results as local files unless materialized in the checkout.
- Prefer host-agnostic Muse resources when generating or changing MCP/tool surfaces.

### Claude chat without repository mutation

- Produce a bounded proposal, patch, or artifact.
- Mark every unperformed command or mutation.
- Do not represent memory, a conversation summary, or an MCP projection as repository authority.

## Skills and hooks

Skills provide reusable procedure. Hooks provide mechanical observation or enforcement. Neither should duplicate the full constitution.

Useful hook projections include:

- append an event after a tool or file mutation,
- warn on direct board frontmatter edits,
- run a relevant static gate before completion,
- preserve session/run identifiers,
- and surface missing Receipt River or mycology records.

Hooks must fail observably and must not silently rewrite source history.
