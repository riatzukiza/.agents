---
name: session-source-recovery
description: "Recover lost source files from pi session history when files were deleted but never committed to git."
triggers:
  - "file was deleted"
  - "file is missing"
  - "recover from session"
  - "session history"
---

# Session Source Recovery

## Problem
An agent deleted or lost source files that were never committed to git. The files may still exist in pi session JSONL files as tool call arguments (e.g., `apply_patch` calls).

## Recovery Steps

### 1. Locate Sessions
Search pi session history for references to the lost files:
```bash
grep -rl "filename-or-package-name" ~/.pi/agent/sessions/ --include="*.jsonl"
```

### 2. Extract Patches
Pi sessions store `apply_patch` tool calls as JSON with `patchText` fields containing `*** Begin Patch / *** End Patch` blocks.

### 3. Parse and Reconstruct
Use Python to extract file contents from JSONL:
```python
import json, re
# Read JSONL, find toolCall blocks with name="apply_patch"
# Extract patchText, split on *** Add/Modify File: directives
# Strip leading + and line numbers from patch content
```

### 4. Verify
- Check TypeScript types match imports
- Run `tsc --noEmit` if possible
- Cross-reference with other sessions for the most recent version

## Session JSONL Structure
- Root type: `session` (metadata)
- Messages: type `message` with `message.role` and `message.content` (list of blocks)
- Tool calls: blocks with `type: "toolCall"`, `name: "apply_patch"`, `arguments.patchText`
- Tool results: separate `message` entries with matching tool call IDs

## Caveats
- Only works if the session JSONL files haven't been purged
- Files created via `write` tool (not `apply_patch`) may have different structure
- Binary files cannot be recovered from text JSONL
- Multiple sessions may have different versions — prefer the most recent
