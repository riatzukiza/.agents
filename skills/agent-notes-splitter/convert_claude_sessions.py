#!/usr/bin/env python3
"""Convert Claude Code JSONL session logs to markdown conversation exports.

Reads session JSONL files from the Claude Code project directory and writes
markdown exports that resemble the original Claude Code export format.
"""

import json
import re
from pathlib import Path

CLAUDE_PROJECT_DIR = Path("/home/err/.claude/projects/-home-err-spaces-Truth")
OUTPUT_DIR = Path("/home/err/spaces/Truth/docs/notes")


def strip_xml(text):
    """Remove simple XML tags from command content."""
    return re.sub(r"</?[^>]+>", "", text).strip()


def format_command(content):
    """Convert XML command markup to plain command text."""
    # <command-name>/model</command-name>... -> /model
    m = re.search(r"<command-name>(.*?)</command-name>", content)
    if m:
        return m.group(1)
    # <command-message>usage</command-message> -> usage
    m = re.search(r"<command-message>(.*?)</command-message>", content)
    if m:
        return m.group(1)
    # <local-command-stdout>...</local-command-stdout> -> strip
    content = re.sub(r"</?local-command-[^>]*>", "", content)
    return content.strip()


def truncate_content(text, max_lines=60, head_lines=40, tail_lines=20):
    """Truncate long multi-line text with a hidden-lines marker."""
    lines = text.splitlines()
    if len(lines) <= max_lines:
        return text
    head = lines[:head_lines]
    tail = lines[-tail_lines:]
    hidden = len(lines) - head_lines - tail_lines
    return (
        "\n".join(head)
        + f"\n──── ({hidden} lines hidden) ──────────────────────────────────────────────\n"
        + "\n".join(tail)
    )


def format_user_content(content):
    """Format user message content, truncating long tool results.

    Returns a list of (prefix, text) tuples. Prompts use the user bullet;
    tool results use the result marker.
    """
    if isinstance(content, str):
        if not content:
            return []
        # Strip embedded file content when the prompt starts with @filename.
        # Keep the first line (the reference and any short prompt).
        if content.startswith("@"):
            return [("❯", content.split("\n", 1)[0])]
        # Commands.
        if "<command-name>" in content or "<command-message>" in content:
            cmd = format_command(content)
            if cmd:
                return [("❯", cmd)]
            return []
        return [("❯", content)]

    if isinstance(content, list):
        # Tool results from previous turns.
        result_lines = []
        for block in content:
            btype = block.get("type")
            if btype == "tool_result":
                result = block.get("content", "")
                if isinstance(result, str) and result:
                    result_lines.append(("⎿", truncate_content(result)))
            elif btype == "text":
                text = block.get("text", "")
                if text:
                    result_lines.append(("⎿", text))
        return result_lines
    return []


def extract_text(content_blocks):
    """Extract text and tool_use descriptions from assistant content."""
    parts = []
    tools = []
    for block in content_blocks:
        btype = block.get("type")
        if btype == "text":
            parts.append(block.get("text", ""))
        elif btype == "thinking":
            # Encrypted/signed thinking; skip.
            pass
        elif btype == "tool_use":
            name = block.get("name", "tool")
            tools.append(name)
    text = "\n".join(parts).strip()
    return text, tools


def format_system_message(obj):
    """Format system/tool/queue messages."""
    subtype = obj.get("subtype", "")
    content = obj.get("content", "")
    if not content:
        return ""
    if subtype == "local_command":
        cmd = format_command(content)
        if cmd:
            return f"⎿ {cmd}"
        return ""
    # Queue operation or generic system message.
    text = strip_xml(content)
    if text:
        return f"  ⎿ {text}"
    return ""


def format_attachment(obj):
    """Format file attachment."""
    att = obj.get("attachment", {})
    filename = att.get("filename", "")
    return f"❯ {filename}"


def convert_session(jsonl_path):
    """Convert a single JSONL session to markdown text."""
    lines = []
    header_shown = False
    version = "2.1.193"

    with open(jsonl_path, "r", encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            obj = json.loads(line)
            t = obj.get("type")

            if t == "user":
                raw_content = obj.get("message", {}).get("content", "")
                if not raw_content:
                    continue
                # Skip meta caveat.
                if obj.get("isMeta") and isinstance(raw_content, str) and "local-command-caveat" in raw_content:
                    continue
                entries = format_user_content(raw_content)
                for prefix, text in entries:
                    if text:
                        # Multi-line tool results need each line indented.
                        for i, para in enumerate(text.split("\n")):
                            if i == 0:
                                lines.append(f"{prefix} {para}")
                            else:
                                lines.append(f"  {para}")

            elif t == "assistant":
                content_blocks = obj.get("message", {}).get("content", [])
                text, tools = extract_text(content_blocks)
                if text:
                    # Assistant messages may span multiple lines; prefix each
                    # paragraph line with the bullet for consistency.
                    for i, para in enumerate(text.split("\n")):
                        if i == 0:
                            lines.append(f"● {para}")
                        else:
                            lines.append(f"  {para}")
                for tool in tools:
                    lines.append(f"● ToolUse({tool})")

            elif t == "system":
                formatted = format_system_message(obj)
                if formatted:
                    lines.append(formatted)

            elif t == "attachment":
                lines.append(format_attachment(obj))

            elif t == "queue-operation":
                content = obj.get("content", "")
                text = strip_xml(content)
                if text:
                    lines.append(f"  ⎿ {text}")

            elif t in ("mode", "permission-mode"):
                pass
            elif t == "ai-title":
                pass
            elif t == "last-prompt":
                pass
            elif t == "file-history-snapshot":
                pass

    # Add header if not present.
    header = [
        " ▐▛███▜▌   Claude Code v2.1.193",
        "▝▜█████▛▘  Opus 4.8 · Claude Team",
        "  ▘▘ ▝▝    ~/spaces/Truth",
        "",
        "",
    ]
    return "\n".join(header + lines)


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for jsonl_path in sorted(CLAUDE_PROJECT_DIR.glob("*.jsonl")):
        # Skip tiny /status or /upgrade only sessions (under 2KB).
        if jsonl_path.stat().st_size < 2000:
            continue
        markdown = convert_session(jsonl_path)
        out_name = f"claude-session-{jsonl_path.stem}.md"
        out_path = OUTPUT_DIR / out_name
        out_path.write_text(markdown, encoding="utf-8")
        print(f"Wrote {out_path.name} ({len(markdown)/1024:.1f}K)")


if __name__ == "__main__":
    main()
