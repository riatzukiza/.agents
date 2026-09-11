---
name: agent-notes-splitter
description: "Split large agent-conversation markdown exports into manageable, topic-bounded chunks and archive the originals"
license: GPL-3.0-or-later
compatibility:
  - opencode >=1.0.0
metadata:
  author: opencode
  version: 1
---

# Skill: Agent Notes Splitter

## Goal
Break down oversized agent-conversation markdown exports (Claude Code, Perplexity, etc.) into smaller, topic-bounded chunks so notes directories stay navigable and searchable. Archive the originals and produce an index.

## Use This Skill When
- The user asks to split, break down, or make manageable the files in `docs/notes/`.
- A notes directory contains markdown exports larger than ~350 lines or ~50 KB.
- Claude session exports need to be recovered from local JSONL logs and re-exported before splitting.

## Do Not Use This Skill When
- Notes are already small (under ~350 lines); leave them whole.
- The user is asking for content editing or summarization rather than size/archival organization.

## Inputs
- Path to the notes directory (default: `docs/notes/`).
- Whether Claude session exports need recovery (default: only if originals are missing or if explicitly requested).

## Steps

1. **Inventory** the notes directory. List files with line counts and sizes. Identify which exceed the split threshold (~350 lines).

2. **Recover Claude exports if needed.** Claude CLI has no built-in export command. Session data lives in:
   ```
   ~/.claude/projects/<project-path>/<session-id>.jsonl
   ```
   Convert these JSONL logs to markdown using the bundled `convert_claude_sessions.py`. Skip tiny sessions (< 2 KB) and system-only noise. Rename outputs by session topic (e.g., `phase-0.md`, `claude-physics-merge.md`).

3. **Split** large markdown files with `split_notes.py`. The splitter:
   - Detects Perplexity vs Claude export formats.
   - Splits at natural boundaries: Perplexity `--- / #` section breaks; Claude `●` assistant turns and `❯` user prompts.
   - Names chunks as `<original>-<seq>-<topic-slug>.md`.
   - Archives originals to `<notes-dir>/archive/`.
   - Writes `<notes-dir>/index.md` mapping originals to chunks.

4. **Verify** the result:
   - Chunk count matches the manifest.
   - No original remains in the root except small untouched files.
   - `index.md` is present and readable.

5. **Clean up** any stray intermediate files (e.g., duplicated `claude-session-*.md` left by a previous run).

## Output
- Split markdown chunks in the notes directory.
- `archive/` directory containing the originals.
- `index.md` listing originals and their chunks with line counts and headings.

## References
- Bundled scripts:
  - `convert_claude_sessions.py` — turn Claude JSONL project logs into markdown exports.
  - `split_notes.py` — split markdown exports by conversation boundaries.
- Example workspace: `docs/notes/` in Gates of Truth.

## Strong Hints
- Before running, ensure the notes directory is in a known git state. If you delete originals, recover them from git or from the Claude JSONL logs, not by guessing.
- Do not split on raw byte counts; conversation boundaries keep context coherent.
- Truncate long tool outputs inside Claude re-exports with a visible `──── (N lines hidden) ────` marker rather than omitting them entirely.
