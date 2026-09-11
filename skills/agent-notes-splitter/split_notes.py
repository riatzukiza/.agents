#!/usr/bin/env python3
"""Split large agent-conversation markdown exports into manageable chunks.

Preserves originals in an archive directory and writes chunked files next to
them. Splits are driven by natural conversation boundaries, not raw byte counts.
"""

import os
import re
import shutil
from pathlib import Path

NOTES_DIR = Path("/home/err/spaces/Truth/docs/notes")
ARCHIVE_DIR = NOTES_DIR / "archive"

# Files below this line count are left untouched.
MIN_LINES_TO_SPLIT = 350

# Target chunk size in lines. A split only happens at a boundary, so actual
# chunks may be larger until the next safe break point.
TARGET_LINES = 300

# Minimum accumulated lines before we are willing to split at a boundary.
MIN_LINES_BEFORE_SPLIT = 120


def detect_kind(lines):
    """Detect whether a file is a Perplexity export or a Claude Code export."""
    head = "".join(lines[:10])
    if "perplexity" in head.lower():
        return "perplexity"
    if "Claude Code" in head or "▐▛███▜▌" in head:
        return "claude"
    return "generic"


def is_boundary(lines, idx, kind):
    """Return True if lines[idx] starts a new natural chunk boundary."""
    if idx >= len(lines):
        return False
    line = lines[idx]
    nxt = lines[idx + 1] if idx + 1 < len(lines) else ""
    nxt2 = lines[idx + 2] if idx + 2 < len(lines) else ""

    if kind == "perplexity":
        # Boundary: ---\n\n# (new query/response section)
        if re.match(r"^---\s*$", line) and nxt.strip() == "":
            if nxt2.startswith("#"):
                return True
        # Also treat standalone --- followed by ## as boundary if chunk is large.
        if re.match(r"^---\s*$", line) and nxt.strip() == "":
            if nxt2.startswith("##"):
                return True
        return False

    if kind == "claude":
        # Boundary: assistant turn that starts a new major statement.
        # "● " followed by non-whitespace content.
        if line.startswith("● ") and len(line) > 2:
            return True
        # Boundary: user prompt line (but only when it is a new prompt, not a
        # continuation marker). Claude Code exports prefix user input with "❯ ".
        if line.startswith("❯ ") and len(line) > 2:
            return True
        return False

    # Generic: split on level-1 headings.
    if re.match(r"^# ", line):
        return True
    return False


def first_heading(lines):
    """Extract a short heading from the first few lines for a chunk filename."""
    # First pass: prefer level-2 headings (more specific than the question
    # title in Perplexity exports, and usually the real topic).
    for line in lines:
        m = re.match(r"^##\s+(.*)", line)
        if m:
            return m.group(1).strip()

    # For Claude Code exports with no headings, use the first meaningful
    # assistant or user statement, skipping the ASCII header and meta noise.
    for line in lines:
        stripped = line.strip()
        # Skip ASCII art, empty lines, and continuation markers.
        if not stripped or any(c in stripped for c in "▐▛▜▌▝▘▞▚"):
            continue
        # Skip XML-escaped command output, tool-use announcements, and code lines.
        if (
            "local-command-stdout" in stripped
            or "ToolUse(" in stripped
            or "Bash completed with no output" in stripped
            or "claude/projects/" in stripped
            or "/home/err/.claude" in stripped
        ):
            continue
        # Skip result markers and continuation lines.
        if stripped.startswith("⎿") or stripped.startswith("  ⎿"):
            continue
        # Skip numbered code lines (tool results often start with line numbers).
        if re.match(r"^\d+\s+[(\[]", stripped):
            continue
        # Skip bare file paths (attachments and tool results).
        if re.match(r"^(❯\s+)?/[^\s]+\.(md|clj|json|edn|txt|py|js|ts|css|html|yml|yaml)$", stripped):
            continue
        # Prefer assistant turns.
        if stripped.startswith("● "):
            body = stripped[2:].strip()
            # Skip tool announcements that make poor filenames.
            if body.startswith(("Bash(", "Read ", "Update(", "Write(")):
                continue
            return body
        # Accept user prompts, but skip meta commands, XML wrappers, and bare
        # attachment references that produce ugly filenames.
        if stripped.startswith("❯ "):
            body = stripped[2:].strip()
            if body.startswith(("/", "<")):
                continue
            # Skip if the prompt is only a list of @file paths.
            if re.match(r"^@\S+\.(md|clj|json|edn|txt|py|js|ts|css|html|yml|yaml)(\s+@\S+\.\w+)*\s*$", body):
                continue
            return body

    # Fall back to any markdown heading.
    for line in lines:
        m = re.match(r"^#+\s+(.*)", line)
        if m:
            return m.group(1).strip()
    return "start"



def slugify(text, max_len=40):
    """Make a filesystem-safe slug from a heading."""
    s = re.sub(r"[^\w\s-]", "", text).strip().lower()
    s = re.sub(r"[-\s]+", "-", s)
    return s[:max_len].rstrip("-")


def split_file(path, archive_dir):
    """Split a single markdown file and return a list of written chunk paths."""
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    if len(lines) <= MIN_LINES_TO_SPLIT:
        return []

    kind = detect_kind(lines)
    base = path.stem
    ext = path.suffix

    chunks = []
    current = []
    current_start = 0

    def flush(force=False):
        nonlocal current, current_start, chunks
        if not current:
            return
        # Keep accumulating until we hit target size or are forced.
        if not force and len(current) < TARGET_LINES:
            return
        # Find a heading to name this chunk.
        heading = first_heading(current)
        slug = slugify(heading)
        seq = len(chunks) + 1
        name = f"{base}-{seq:03d}-{slug}{ext}"
        out_path = path.parent / name
        with open(out_path, "w", encoding="utf-8") as f:
            f.writelines(current)
        chunks.append((out_path, len(current), heading))
        current = []

    for idx, line in enumerate(lines):
        # Check for boundary *before* adding the line so the boundary line
        # becomes the first line of the next chunk.
        if is_boundary(lines, idx, kind):
            if len(current) >= MIN_LINES_BEFORE_SPLIT:
                flush(force=False)
            elif len(current) >= TARGET_LINES:
                flush(force=False)
        current.append(line)

    flush(force=True)

    if not chunks:
        return []

    # Move original to archive.
    archive_dir.mkdir(parents=True, exist_ok=True)
    archive_path = archive_dir / path.name
    shutil.move(str(path), str(archive_path))
    return chunks


def main():
    archive_dir = ARCHIVE_DIR
    manifest = []

    md_files = sorted(NOTES_DIR.glob("*.md"))
    for path in md_files:
        if path.name in {"README.md", "index.md"}:
            continue
        # Skip files that are already chunks from a previous run.
        if re.search(r"-\d{3}-", path.name):
            continue
        chunks = split_file(path, archive_dir)
        if chunks:
            manifest.append((path.name, chunks))
        # Empty files are removed; tiny files are left as-is.
        elif path.stat().st_size == 0:
            path.unlink()

    # Write a small index.
    if manifest:
        index_path = NOTES_DIR / "index.md"
        with open(index_path, "w", encoding="utf-8") as f:
            f.write("# docs/notes index\n\n")
            f.write("Large agent-conversation exports have been split into ")
            f.write("smaller, topic-bounded chunks. Originals are archived in ")
            f.write("`archive/`.\n\n")
            for original, chunks in manifest:
                f.write(f"## {original}\n\n")
                for chunk_path, line_count, heading in chunks:
                    f.write(f"- `{chunk_path.name}` ({line_count} lines) — {heading}\n")
                f.write("\n")

    # Print summary.
    print(f"Archived originals to: {archive_dir}")
    for original, chunks in manifest:
        print(f"{original}: split into {len(chunks)} chunks")


if __name__ == "__main__":
    main()
