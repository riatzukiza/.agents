#!/usr/bin/env python3
"""Reject generated dependencies and non-portable tracked symlinks."""

from __future__ import annotations

import os
import posixpath
import subprocess
import sys
from collections import deque
from collections.abc import Iterable
from pathlib import PurePosixPath, PureWindowsPath


SYMLINK_MODE = "120000"
MAX_SYMLINK_EXPANSIONS = 40


def target_is_absolute(target: str) -> bool:
    """Return whether a target is rooted on POSIX or Windows."""
    windows_target = PureWindowsPath(target)
    return bool(
        PurePosixPath(target).is_absolute()
        or windows_target.is_absolute()
        or windows_target.drive
        or windows_target.root
    )


def windows_path_key(path: str) -> str:
    """Return a separator-normalized, case-insensitive Windows path key."""
    # NTFS case-insensitive lookup uses an uppercase table, not Unicode
    # case-folding (for example, dotless-i and ASCII I compare equal there).
    return path.replace("\\", "/").upper()


def ancestor_keys(path_key: str) -> list[str]:
    """Return component-aligned ancestor keys for a normalized tracked path."""
    parts = path_key.split("/")
    return ["/".join(parts[:index]) for index in range(1, len(parts))]


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

    if "\0" in target:
        errors.append(f"{path}: tracked symlink target contains NUL")
        return errors

    if target_is_absolute(target):
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


def symlink_chain_error(
    path: str, target: str, symlink_targets: dict[str, str]
) -> str | None:
    """Resolve tracked symlinks component-by-component and reject escape or cycles."""
    pending = deque(PurePosixPath(posixpath.dirname(path)).parts)
    pending.extend(target.replace("\\", "/").split("/"))
    resolved: list[str] = []
    seen_states: set[tuple[tuple[str, ...], tuple[str, ...]]] = set()
    expansions = 0

    while pending:
        state = (tuple(resolved), tuple(pending))
        if state in seen_states:
            return f"{path}: tracked symlink chain contains a cycle"
        seen_states.add(state)

        component = pending.popleft()
        if component in {"", "."}:
            continue
        if component == "..":
            if not resolved:
                return (
                    f"{path}: tracked symlink chain escapes the repository: "
                    f"{target!r}"
                )
            resolved.pop()
            continue

        resolved.append(component)
        candidate = "/".join(resolved)
        nested_target = symlink_targets.get(windows_path_key(candidate))
        if nested_target is None:
            continue
        expansions += 1
        if expansions > MAX_SYMLINK_EXPANSIONS:
            return (
                f"{path}: tracked symlink chain exceeds "
                f"{MAX_SYMLINK_EXPANSIONS} expansions (cycle or excessive indirection)"
            )
        if target_is_absolute(nested_target):
            return (
                f"{path}: tracked symlink chain reaches absolute target "
                f"{nested_target!r} through {candidate!r}"
            )

        resolved.pop()
        nested_components = nested_target.replace("\\", "/").split("/")
        pending.extendleft(reversed(nested_components))

    return None


def audit_entries(entries: Iterable[tuple[str, str, str | None]]) -> list[str]:
    """Return all portability violations for tracked entries."""
    entries = list(entries)
    errors: list[str] = []
    symlink_targets: dict[str, str] = {}
    tracked_paths: dict[str, str] = {}
    tracked_prefixes: dict[str, str] = {}
    for mode, path, target in entries:
        key = windows_path_key(path)
        prior_path = tracked_paths.get(key)
        if prior_path is not None and prior_path != path:
            errors.append(
                f"{path}: tracked path collides case-insensitively with {prior_path}"
            )
        prior_ancestor = next(
            (
                tracked_paths[ancestor]
                for ancestor in ancestor_keys(key)
                if ancestor in tracked_paths
            ),
            None,
        )
        if prior_ancestor is not None:
            errors.append(
                f"{path}: tracked path descends case-insensitively from file path "
                f"{prior_ancestor}"
            )
        prior_descendant = tracked_prefixes.get(key)
        if prior_descendant is not None:
            errors.append(
                f"{path}: tracked path collides case-insensitively as a "
                f"file/directory prefix with {prior_descendant}"
            )

        tracked_paths.setdefault(key, path)
        for ancestor in ancestor_keys(key):
            tracked_prefixes.setdefault(ancestor, path)
        if mode == SYMLINK_MODE and target is not None:
            symlink_targets.setdefault(key, target)

    for mode, path, target in entries:
        direct_errors = entry_errors(mode, path, target)
        errors.extend(direct_errors)
        if mode == SYMLINK_MODE and target and not direct_errors:
            chain_error = symlink_chain_error(path, target, symlink_targets)
            if chain_error:
                errors.append(chain_error)
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
    """Audit the current Git index and return a process exit code."""
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
