---
name: kanban-board-ui
description: Kanban web UI with drag-and-drop columns, sidebar detail view, frontmatter editing, markdown rendering, and comments. Uses uxx token styling.
---

# Skill: Kanban Board UI

## Goal
Run and interact with the kanban web UI — a React-based board with sidebar detail view, frontmatter editing, rendered markdown, and comment system.

## Use This Skill When
- User wants to visually manage kanban tasks.
- User wants to drag tasks between columns.
- User wants to view rendered markdown content of tasks.
- User wants to edit frontmatter fields visually.
- User wants to add comments to tasks through the UI.

## Do Not Use This Skill When
- User wants CLI-only operations (use `eta-mu-kanban` skill).
- User wants Trello sync (use `kanban sync trello`).

## Starting the UI

### Via PM2 (persistent)
```bash
cd services/eta-mu/kanban
pm2 start ecosystem.config.cjs
# → http://127.0.0.1:8791
```

### Via CLI (one-off)
```bash
eta-mu kanban serve --tasks-dir <path> --port 8791
```

## UI Features

### Board View
- Columns for each status (icebox, incoming, todo, in_progress, review, done, etc.)
- Cards show title, priority badge, and labels
- Drag-and-drop cards between columns to change status
- Filter bar to search by title/labels/path
- Reload button to refresh from disk

### Sidebar Detail View
- Click a card to open the sidebar (board stays interactive)
- **Frontmatter section**: all YAML fields displayed as labeled rows
  - Double-click any field to edit inline
  - `status` → dropdown (all valid statuses)
  - `priority` → dropdown (P0–P3)
  - `labels` → comma-separated input
  - `points` → number input
  - Other fields → text input
  - Blur or Enter to save; Escape to cancel
- **Body sections**: rendered markdown (react-markdown + remark-gfm)
- **Comment sections**: styled with accent left-border, muted text
- **Add comment**: textarea + button at bottom
- **Open in editor**: pencil button (✎) opens file in `$EDITOR`

### Section Parsing
- YAML frontmatter (between `---` at file start) → parsed as editable fields
- After frontmatter, `---` toggles between body and comment sections
- Body → rendered as markdown
- Comments → rendered as styled blocks (accent border, muted color)

## Architecture
- **Frontend**: React + Vite, built to `dist/web/`
- **Styling**: uxx token CSS variables (`tokens.css` from `@open-hax/uxx`)
- **Markdown**: `react-markdown` + `remark-gfm`
- **Backend**: Node.js HTTP server (`src/server.ts`)
- **API**:
  - `GET /api/board` — board snapshot JSON
  - `POST /api/task/:uuid/status` — move task
  - `GET /api/task/:uuid/content` — parsed frontmatter + sections
  - `PATCH /api/task/:uuid/frontmatter` — update field `{ key, value }`
  - `POST /api/task/:uuid/comment` — add comment `{ text }`
  - `POST /api/task/:uuid/open-editor` — open in `$EDITOR`
- **Build**: `pnpm build` runs `tsc` then `vite build`
- **Config**: `openhax.kanban.json` in tasks directory

## Dependencies
- `react`, `react-dom` (UI framework)
- `react-markdown`, `remark-gfm` (markdown rendering)
- `gray-matter` (YAML frontmatter parsing)
- `vite`, `@vitejs/plugin-react` (build tooling)
- uxx tokens CSS (styling — loaded from `@open-hax/uxx/dist/tokens.css`)

## Environment Variables
| Variable | Default | Description |
|----------|---------|-------------|
| `KANBAN_HOST` | `127.0.0.1` | Bind address |
| `KANBAN_PORT` | `8791` | Bind port |
| `EDITOR` / `VISUAL` | `xdg-open` | Editor for "Open in editor" button |
