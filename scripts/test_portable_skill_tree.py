#!/usr/bin/env python3
"""Unit tests for the portable skill-tree policy."""

from __future__ import annotations

import unittest

from check_portable_skill_tree import (
    audit_entries,
    entry_errors,
    symlink_chain_error,
    windows_path_key,
)


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

    def test_rejects_windows_reserved_device_names(self) -> None:
        """Legacy Windows device names remain reserved before extensions."""
        errors = audit_entries(
            [
                ("100644", "skills/example/CON", None),
                ("100644", "skills/example/aux.txt", None),
                ("100644", "skills/example/COM¹.log", None),
                ("100644", "skills/example/LPT9", None),
            ]
        )
        self.assertEqual(
            sum("reserved Windows device name" in error for error in errors),
            4,
        )

    def test_rejects_windows_console_device_aliases(self) -> None:
        """Win32 console input and output aliases remain reserved."""
        errors = audit_entries(
            [
                ("100644", "skills/example/CONIN$", None),
                ("100644", "skills/example/conout$.txt", None),
            ]
        )
        self.assertEqual(
            sum("reserved Windows device name" in error for error in errors),
            2,
        )

    def test_rejects_ntfs_git_admin_aliases(self) -> None:
        """Git administrative names and their NTFS short alias are rejected."""
        errors = audit_entries(
            [
                ("100644", "foo/.GIT/config", None),
                ("100644", "foo/git~1/config", None),
                ("100644", "foo/GIT~1.../config", None),
            ]
        )
        self.assertEqual(
            sum("core.protectNTFS" in error for error in errors),
            3,
        )

    def test_rejects_windows_trailing_period_and_space(self) -> None:
        """Windows-incompatible trailing periods and spaces are rejected."""
        errors = audit_entries(
            [
                ("100644", "skills/example/file.", None),
                ("100644", "skills/example/file ", None),
            ]
        )
        self.assertEqual(
            sum("Windows-reserved space or period" in error for error in errors),
            2,
        )

    def test_rejects_windows_reserved_and_control_characters(self) -> None:
        """Win32-reserved punctuation and control characters are rejected."""
        errors = audit_entries(
            [
                ("100644", "skills/example/bad:name", None),
                ("100644", "skills/example/bad\x1fname", None),
            ]
        )
        self.assertEqual(
            sum("Windows-reserved character" in error for error in errors),
            2,
        )

    def test_rejects_surrogate_escaped_tracked_path(self) -> None:
        """Invalid UTF-8 path bytes cannot pass through surrogate escape."""
        errors = audit_entries(
            [("100644", "skills/example/bad\udc80name", None)]
        )
        self.assertTrue(any("invalid Unicode surrogate" in error for error in errors))

    def test_surrogate_path_diagnostics_are_utf8_safe(self) -> None:
        """Every diagnostic remains encodable when one path has multiple errors."""
        errors = audit_entries(
            [("100644", "skills/example/bad\udc80:name", None)]
        )
        self.assertGreaterEqual(len(errors), 2)
        for error in errors:
            error.encode("utf-8")

    def test_rejects_expanding_upcase_without_false_collision(self) -> None:
        """Expanding Unicode uppercase is rejected without collapsing NTFS keys."""
        self.assertNotEqual(
            windows_path_key("a/straße"),
            windows_path_key("a/STRASSE"),
        )
        errors = audit_entries(
            [
                ("100644", "a/straße", None),
                ("100644", "a/STRASSE", None),
            ]
        )
        self.assertTrue(any("length-preserving Windows upcase" in error for error in errors))
        self.assertFalse(any("collides case-insensitively" in error for error in errors))

    def test_rejects_supplementary_upcase_without_false_collision(self) -> None:
        """Non-BMP case mappings cannot collapse distinct NTFS code-unit keys."""
        self.assertNotEqual(
            windows_path_key("a/𐐨"),
            windows_path_key("a/𐐀"),
        )
        errors = audit_entries(
            [
                ("100644", "a/𐐨", None),
                ("100644", "a/𐐀", None),
            ]
        )
        self.assertTrue(
            any("supplementary-plane Windows upcase" in error for error in errors)
        )
        self.assertFalse(any("collides case-insensitively" in error for error in errors))

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

    def test_normalizes_backslash_symlink_parent_before_containment(self) -> None:
        """A backslash path is invalid but its contained target is not an escape."""
        errors = entry_errors("120000", r"a\dir\link", "../target")
        self.assertTrue(any("Windows-reserved character" in error for error in errors))
        self.assertFalse(any("target escapes" in error for error in errors))

    def test_normalizes_backslash_parent_in_chain_resolver(self) -> None:
        """Chain resolution derives the parent after Windows path normalization."""
        self.assertIsNone(
            symlink_chain_error(r"a\dir\link", "../target", {})
        )

    def test_rejects_nul_symlink_target(self) -> None:
        """A NUL-bearing target that cannot be checked out is rejected."""
        errors = entry_errors("120000", "skills/example/link", "inside\0outside")
        self.assertTrue(any("NUL" in error for error in errors))

    def test_rejects_surrogate_escaped_symlink_target(self) -> None:
        """Invalid UTF-8 symlink bytes cannot become a Win32 target."""
        errors = entry_errors("120000", "skills/example/link", "inside\udc80outside")
        self.assertTrue(any("invalid Unicode surrogate" in error for error in errors))

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

    def test_rejects_windows_dot_component_collision(self) -> None:
        """Windows separators and dot components cannot hide a path collision."""
        errors = audit_entries(
            [
                ("100644", r"a\x\..\dir", None),
                ("100644", "a/dir", None),
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

    def test_rejects_dot_component_symlink_chain_escape(self) -> None:
        """A Windows-equivalent dot path cannot hide a chained symlink escape."""
        errors = audit_entries(
            [
                ("120000", r"a\x\..\dir", "../b"),
                ("120000", "a/link", "DIR/../../outside"),
            ]
        )
        self.assertTrue(any("chain escapes" in error for error in errors))

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
