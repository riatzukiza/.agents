#!/usr/bin/env python3
"""Unit tests for the portable skill-tree policy."""

from __future__ import annotations

import unittest

from check_portable_skill_tree import audit_entries, entry_errors


class EntryErrorsTest(unittest.TestCase):
    def test_accepts_regular_source(self) -> None:
        self.assertEqual(entry_errors("100644", "skills/example/SKILL.md"), [])

    def test_accepts_contained_relative_symlink(self) -> None:
        self.assertEqual(
            entry_errors("120000", ".opencode/skill/example", "../../skills/example"),
            [],
        )

    def test_rejects_posix_absolute_symlink(self) -> None:
        errors = entry_errors("120000", "skills/example/link", "/home/user/example")
        self.assertTrue(any("absolute" in error for error in errors))

    def test_rejects_windows_absolute_symlink(self) -> None:
        errors = entry_errors("120000", "skills/example/link", r"C:\\Users\\me\\example")
        self.assertTrue(any("absolute" in error for error in errors))

    def test_rejects_windows_rooted_symlink_without_drive(self) -> None:
        errors = entry_errors("120000", "skills/example/link", r"\outside")
        self.assertTrue(any("absolute" in error for error in errors))

    def test_rejects_escaping_symlink(self) -> None:
        errors = entry_errors("120000", "skills/example/link", "../../../outside")
        self.assertTrue(any("escapes" in error for error in errors))

    def test_rejects_windows_style_escape(self) -> None:
        errors = entry_errors("120000", "skills/example/link", r"..\\..\\..\\outside")
        self.assertTrue(any("escapes" in error for error in errors))

    def test_rejects_tracked_dependency_tree(self) -> None:
        errors = audit_entries(
            [("100644", "skills/example/node_modules/package/index.js", None)]
        )
        self.assertTrue(any("node_modules" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
