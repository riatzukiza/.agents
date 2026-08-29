#!/usr/bin/env python3
"""Reject generated dependencies and non-portable tracked symlinks."""

from __future__ import annotations

import os
import posixpath
import subprocess
import sys
from collections.abc import Iterable
from pathlib import PurePosixPath, PureWindowsPath


SYMLINK_MODE = "120000"


def entry_errors(mode: str, path: str, target: str | None = None) -> list[str]:
    """Return portability violations for one tracked Git entry."""
    errors: list[str] = []
    parts = PurePosixPath(path).parts

    if any(part.lower() == "node_modules" for part in parts):
        errors.append(f"{path}: generated node_modules content is tracked")

    if mode != SYMLINK_MODE:
        return errors

    if target is None or target == "":
        errors.append(f"{path}: tracked symlink has an empty or unreadable target")
        return errors

    windows_target = PureWindowsPath(target)
    if PurePosixPath(target).is_absolute() or windows_target.is_absolute() or windows_target.drive:
        errors.append(f"{path}: tracked symlink target is absolute: {target!r}")
        return errors

    # Git stores slash-separated paths. Treat backslashes as separators too so
    # a target cannot pass on Linux and escape after a Windows checkout.
    portable_target = target.replace("\\", "/")
    destination = posixpath.normpath(
        posixpath.join(posixpath.dirname(path), portable_target)
    )
    if destination == ".." or destination.startswith("../"):
        errors.append(f"{path}: tracked symlink target escapes the repository: {target!r}")

    return errors


def audit_entries(entries: Iterable[tuple[str, str, str | None]]) -> list[str]:
    """Return all portability violations for tracked entries."""
    errors: list[str] = []
    for mode, path, target in entries:
        errors.extend(entry_errors(mode, path, target))
    return errors


def git_entries() -> list[tuple[str, str, str | None]]:
    """Read tracked entries and symlink blobs from the current Git index."""
    listing = subprocess.run(
        ["git", "ls-files", "--stage", "-z"],
        check=True,
        stdout=subprocess.PIPE,
    ).stdout

    entries: list[tuple[str, str, str | None]] = []
    for record in listing.split(b"\0"):
        if not record:
            continue
        metadata, raw_path = record.split(b"\t", 1)
        mode_bytes, oid, _stage = metadata.split(b" ", 2)
        mode = mode_bytes.decode("ascii")
        path = os.fsdecode(raw_path)
        target = None
        if mode == SYMLINK_MODE:
            target = os.fsdecode(
                subprocess.run(
                    ["git", "cat-file", "blob", oid.decode("ascii")],
                    check=True,
                    stdout=subprocess.PIPE,
                ).stdout
            )
        entries.append((mode, path, target))
    return entries


def main() -> int:
    try:
        entries = git_entries()
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        print(f"portable skill tree check could not inspect Git entries: {error}", file=sys.stderr)
        return 2

    errors = audit_entries(entries)
    if errors:
        print("portable skill tree check failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    symlinks = sum(mode == SYMLINK_MODE for mode, _path, _target in entries)
    print(
        f"portable skill tree check passed: {len(entries)} tracked entries, "
        f"{symlinks} contained relative symlink(s)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
