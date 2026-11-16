---
name: eta-mu-kanban
description: Agent-first kanban board CLI via `eta-mu kanban`. List, search, find, count, update status, manage frontmatter, add comments, open in editor, and serve the web UI.
---

# Skill: eta-mu kanban

## Goal
Manage a markdown-backed kanban board through the `eta-mu kanban` CLI — designed for agent-first task management.

## Use This Skill When
- User asks to list, search, find, or count kanban tasks.
- User wants to move a task between columns (update status).
- User wants to view or edit task frontmatter (priority, labels, status, etc.).
- User wants to add a comment to a task.
- User wants to open a task file in their editor.
- User wants to start the kanban web UI.

## Do Not Use This Skill When
- The user is working with Trello directly (use `kanban sync trello`).
- The user wants to create a brand new spec (use `spec-driven-dev`).

## Prerequisites
- The kanban package must be built: `cd orgs/open-hax/eta-mu/packages/kanban && pnpm build`
- Tasks directory must exist with markdown files containing YAML frontmatter.
- For the web UI, the Vite frontend must be built: `pnpm build` (includes tsc + vite).

## Commands

### Discovery
```bash
# List all tasks
eta-mu kanban list --tasks-dir <path>
eta-mu kanban list --tasks-dir <path> --verbose

# Search tasks by title/uuid/labels
eta-mu kanban search "query" --tasks-dir <path>

# Find task by UUID or title substring
eta-mu kanban find <uuid> --tasks-dir <path>

# Show column counts
eta-mu kanban count --tasks-dir <path>
```

### Task Content
```bash
# View parsed task content (frontmatter + sections as JSON)
eta-mu kanban content <uuid> --tasks-dir <path>

# Append a comment to a task (writes ---\ntext\n--- section)
eta-mu kanban comment <uuid> "Comment text here" --tasks-dir <path>

# Update a frontmatter field
eta-mu kanban frontmatter <uuid> status in_progress --tasks-dir <path>
eta-mu kanban frontmatter <uuid> priority P0 --tasks-dir <path>
eta-mu kanban frontmatter <uuid> labels "tasks,5sp" --tasks-dir <path>

# Open task file in $EDITOR
eta-mu kanban open <uuid> --tasks-dir <path>
```

### Web UI & Board
```bash
# Start the kanban web UI (React + uxx tokens)
eta-mu kanban serve --tasks-dir <path> --port 8791

# Generate board snapshot JSON
eta-mu kanban board snapshot --tasks-dir <path> --out <path>
```

## Task File Format

Tasks are markdown files with YAML frontmatter:

```md
---
uuid: "my-task-uuid"
title: "Task Title"
status: todo
priority: P1
labels: ["tasks", "3sp"]
created_at: "2026-05-27T00:00:00Z"
source: "specs/tasks/my-task.md"
points: 3
category: tasks
---

# Task Title

Body content rendered as markdown.

---

This is a comment (between --- delimiters).

---

More body content here.
```

### Section Parsing Rules
- YAML frontmatter between `---` at file start is parsed by gray-matter.
- After frontmatter, `---` on its own line toggles between body and comment sections.
- Body sections are rendered as markdown.
- Comment sections are rendered distinctly (muted, accent border).

## Valid Statuses
`icebox`, `incoming`, `accepted`, `breakdown`, `ready`, `todo`, `in_progress`, `review`, `document`, `done`, `rejected`

## Global Flags
- `--tasks-dir <path>` — Task directory (default: from config or `docs/agile/tasks`)
- `--config <path>` — Path to kanban config file (`openhax.kanban.json`)
- `--port <port>` — Port for serve command (default: 8791)
- `--host <host>` — Host for serve command (default: 127.0.0.1)

## Known Board Locations
- **Knoxx**: `orgs/open-hax/openplanner/packages/agents/knoxx/kanban/`
  - Config: `orgs/open-hax/openplanner/packages/agents/knoxx/kanban/openhax.kanban.json`
  - Import script: `orgs/open-hax/openplanner/packages/agents/knoxx/scripts/import-kanban-specs.mjs`

## PM2 Service
The kanban web UI runs as a PM2 service:
```bash
cd services/eta-mu/kanban
pm2 start ecosystem.config.cjs   # → http://127.0.0.1:8791
pm2 stop eta-mu-kanban
pm2 restart eta-mu-kanban
```

## Workflow Integration
When working on specs or tasks:
1. `eta-mu kanban count` — see current board state
2. `eta-mu kanban search "topic"` — find relevant tasks
3. `eta-mu kanban frontmatter <uuid> status in_progress` — start work
4. `eta-mu kanban comment <uuid> "Progress note"` — record progress
5. `eta-mu kanban frontmatter <uuid> status done` — mark complete
