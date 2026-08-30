#!/usr/bin/env python3
"""Unit tests for the portable skill-tree policy."""

from __future__ import annotations

import unittest

from check_portable_skill_tree import audit_entries, entry_errors


class EntryErrorsTest(unittest.TestCase):
    """Exercise the tracked-tree portability policy."""

    def test_accepts_regular_source(self) -> None:
        """Ordinary tracked source is portable."""
        self.assertEqual(entry_errors("100644", "skills/example/SKILL.md"), [])

    def test_accepts_contained_relative_symlink(self) -> None:
        """A contained relative symlink is portable."""
        self.assertEqual(
            entry_errors("120000", ".opencode/skill/example", "../../skills/example"),
            [],
        )

    def test_rejects_posix_absolute_symlink(self) -> None:
        """A POSIX-absolute target is not portable."""
        errors = entry_errors("120000", "skills/example/link", "/home/user/example")
        self.assertTrue(any("absolute" in error for error in errors))

    def test_rejects_windows_absolute_symlink(self) -> None:
        """A drive-absolute Windows target is not portable."""
        errors = entry_errors("120000", "skills/example/link", r"C:\\Users\\me\\example")
        self.assertTrue(any("absolute" in error for error in errors))

    def test_rejects_windows_rooted_symlink_without_drive(self) -> None:
        """A root-only Windows target is not portable."""
        errors = entry_errors("120000", "skills/example/link", r"\outside")
        self.assertTrue(any("absolute" in error for error in errors))

    def test_rejects_escaping_symlink(self) -> None:
        """A lexically repository-escaping target is rejected."""
        errors = entry_errors("120000", "skills/example/link", "../../../outside")
        self.assertTrue(any("escapes" in error for error in errors))

    def test_rejects_windows_style_escape(self) -> None:
        """A Windows-separator repository escape is rejected."""
        errors = entry_errors("120000", "skills/example/link", r"..\\..\\..\\outside")
        self.assertTrue(any("escapes" in error for error in errors))

    def test_rejects_nul_symlink_target(self) -> None:
        """A NUL-bearing target that cannot be checked out is rejected."""
        errors = entry_errors("120000", "skills/example/link", "inside\0outside")
        self.assertTrue(any("NUL" in error for error in errors))

    def test_rejects_tracked_dependency_tree(self) -> None:
        """Generated dependency content cannot be tracked."""
        errors = audit_entries(
            [("100644", "skills/example/node_modules/package/index.js", None)]
        )
        self.assertTrue(any("node_modules" in error for error in errors))

    def test_accepts_contained_symlink_chain(self) -> None:
        """A fully contained tracked symlink chain is portable."""
        errors = audit_entries(
            [
                ("120000", "a/dir", "../b"),
                ("120000", "a/link", "dir/file"),
            ]
        )
        self.assertEqual(errors, [])

    def test_rejects_escaping_symlink_chain(self) -> None:
        """A chained target that redirects parent traversal outside is rejected."""
        errors = audit_entries(
            [
                ("120000", "a/dir", "../b"),
                ("120000", "a/link", "dir/../../outside"),
            ]
        )
        self.assertTrue(any("chain escapes" in error for error in errors))

    def test_rejects_case_insensitive_escaping_symlink_chain(self) -> None:
        """Windows-equivalent casing cannot hide a chained escape."""
        errors = audit_entries(
            [
                ("120000", "a/dir", "../b"),
                ("120000", "a/link", "DIR/../../outside"),
            ]
        )
        self.assertTrue(any("chain escapes" in error for error in errors))

    def test_rejects_windows_upcase_escaping_symlink_chain(self) -> None:
        """Windows upcasing of dotless-i cannot hide a chained escape."""
        errors = audit_entries(
            [
                ("120000", "a/dır", "../b"),
                ("120000", "a/link", "DIR/../../outside"),
            ]
        )
        self.assertTrue(any("chain escapes" in error for error in errors))

    def test_rejects_case_colliding_symlink_paths(self) -> None:
        """Case-colliding tracked symlinks cannot form a portable tree."""
        errors = audit_entries(
            [
                ("120000", "a/dir", "../b"),
                ("120000", "a/DIR", "../c"),
            ]
        )
        self.assertTrue(any("collides case-insensitively" in error for error in errors))

    def test_rejects_case_colliding_regular_paths(self) -> None:
        """Case-colliding regular files cannot form a portable tree."""
        errors = audit_entries(
            [
                ("100644", "a/file", None),
                ("100644", "a/FILE", None),
            ]
        )
        self.assertTrue(any("collides case-insensitively" in error for error in errors))

    def test_rejects_case_colliding_regular_and_symlink_paths(self) -> None:
        """A regular path and case-equivalent symlink cannot coexist portably."""
        errors = audit_entries(
            [
                ("100644", "a/dir", None),
                ("120000", "a/DIR", "../b"),
            ]
        )
        self.assertTrue(any("collides case-insensitively" in error for error in errors))

    def test_rejects_case_insensitive_file_directory_prefix(self) -> None:
        """One Windows-equivalent path cannot be both a file and directory."""
        errors = audit_entries(
            [
                ("100644", "a", None),
                ("100644", "A/b", None),
            ]
        )
        self.assertTrue(any("descends case-insensitively" in error for error in errors))

    def test_rejects_reverse_file_directory_prefix_order(self) -> None:
        """Prefix collisions are rejected regardless of Git listing order."""
        errors = audit_entries(
            [
                ("100644", "A/b", None),
                ("100644", "a", None),
            ]
        )
        self.assertTrue(any("file/directory prefix" in error for error in errors))

    def test_rejects_symlink_cycle(self) -> None:
        """A tracked symlink cycle is rejected."""
        errors = audit_entries(
            [
                ("120000", "a/one", "two"),
                ("120000", "a/two", "one"),
            ]
        )
        self.assertTrue(any("cycle" in error for error in errors))

    def test_accepts_finite_repeated_symlink_expansion(self) -> None:
        """Finite repeated traversal is not mislabeled as a cycle."""
        errors = audit_entries(
            [
                ("120000", "a/x", "../b"),
                ("120000", "a/link", "x/../a/x/file"),
            ]
        )
        self.assertEqual(errors, [])


if __name__ == "__main__":
    unittest.main()
