---
name: organize-notes
description: "Organize timestamped notes into category directories with slug titles and YAML frontmatter. Use when the user says 'organize my notes', 'clean up my notes folder', or 'organize @docs/notes/'."
---

# organize-notes

## Purpose

When the user says "organize my notes", apply the note organization pattern used in `~/docs/notes/`: convert timestamped filenames into slug titles, add proper YAML frontmatter, and move files into category directories based on content analysis.

## Trigger

- "organize my notes"
- "organize my @docs/notes/"
- "clean up my notes"
- "organize the notes folder"

## Context

The user maintains a notes collection at `~/docs/notes/` with:

- **Category subdirectories**: `design/`, `dev/`, `infrastructure/`, `linux/`, `other/`, `personal/`, `poetry/`, `research/`, `security/`, `empty/`
- **Timestamped notes**: Files named `YYYY.MM.DD.HH.MM.SS.md` that need conversion
- **Slug-titled notes**: Files named with descriptive slugs containing YAML frontmatter
- **README.md**: Auto-generated index of all notes by category

## Pattern

### Existing Frontmatter Pattern

```yaml
---
original_name: "YYYY.MM.DD.HH.MM.SS.md"
title: "Descriptive Title"
summary: "One-sentence summary of content."
category: "category-name"
created: "YYYY-MM-DD"
---

[content]
```

### Organization Steps

1. **Scan for timestamped files**: Match `YYYY.MM.DD.HH.MM.SS.md` pattern in root
2. **Handle empty files**: Move files with 0 bytes to `empty/`
3. **Analyze content**: Read file, extract meaningful title and category
4. **Categories**:
   - `dev/` - Development notes, prompts, agent configs, code
   - `research/` - Technical research, analysis, external references
   - `infrastructure/` - DevOps, servers, configs, deployment
   - `personal/` - Reflections, planning, journal-like content
   - `poetry/` - Lyrics, creative writing, constraints
   - `design/` - Game design, specs, UI mockups
   - `linux/` - Linux-specific configs and notes
   - `security/` - Vulnerability reports, security notes
   - `other/` - Miscellaneous, single-link notes, credentials fragments
5. **Generate slug**: Convert title to kebab-case slug (max ~50 chars)
6. **Write frontmatter**: Add original_name, title, summary, category, created
7. **Move file**: Place in appropriate category directory
8. **Clean up**: Remove Emacs lockfiles (`.#*`), handle conflicts

### Title Extraction Heuristics

- First markdown heading (`# Title`)
- First H1/H2 line
- First sentence of first paragraph (if < 80 chars)
- Content theme (e.g., "AT Protocol Overview" for AT Protocol notes)
- For conversations: "Discord: [Topic]"
- For single links: "Link: [Source Description]"
- For credentials: "Credential [Type] Fragment"

### Summary Generation

- Extract first meaningful paragraph (not metadata)
- Summarize in one sentence (max 150 chars preferred)
- For conversations: describe the topic and participants
- For technical notes: describe the subject matter

## Output

After organizing, report:

- Number of files processed
- Files organized by category
- Files moved to empty/
- Any conflicts or issues

## Example

```
Organized 39 timestamped notes:

- dev/: 12 files
- research/: 9 files  
- personal/: 7 files
- infrastructure/: 4 files
- poetry/: 2 files
- other/: 4 files

Moved 6 empty files to empty/
Removed 4 Emacs lockfiles

Total: 45 files processed
```

## Notes

- Preserve existing frontmatter if file already has it
- Never overwrite existing slug-titled files (merge or conflict)
- Keep original timestamp in `original_name` field
- Generate `created` date from timestamp filename
- Category selection is best-effort, default to `other/`
