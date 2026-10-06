"""Test child-process isolation without executing the fixture program."""
import ast
import os
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch


class ReproducerEnvironmentTest(unittest.TestCase):
    def test_git_overrides_are_removed_from_every_child(self):
        source = Path(__file__).with_name("reproduce.py")
        parsed = ast.parse(source.read_text())
        # Load only the function, so this regression never starts the fixture.
        function = next(n for n in parsed.body if isinstance(n, ast.FunctionDef) and n.name == "run")
        namespace = {"os": os, "subprocess": subprocess}
        exec(compile(ast.Module(body=[function], type_ignores=[]), str(source), "exec"), namespace)
        caller = {"PATH": os.defpath, "FIXTURE_CONTROL": "retained",
                  "GIT_DIR": "/outside/fixture", "GIT_WORK_TREE": "/outside/tree",
                  "GIT_COMMON_DIR": "/outside/common", "GIT_INDEX_FILE": "/outside/index",
                  "GIT_OBJECT_DIRECTORY": "/outside/objects",
                  "GIT_ALTERNATE_OBJECT_DIRECTORIES": "/outside/alternates",
                  "GIT_CONFIG_COUNT": "1", "GIT_CONFIG_KEY_0": "core.hooksPath",
                  "GIT_CONFIG_VALUE_0": "/outside/hooks"}
        with patch.dict(os.environ, caller, clear=True), patch.object(subprocess, "run") as child:
            for command in (["git", "init"], ["bb", "fixture-helper.bb"]):
                namespace["run"](command, Path("/fixture"))
                environment = child.call_args.kwargs.get("env", dict(os.environ))
                self.assertFalse(any(k.startswith("GIT_") for k in environment))
                self.assertEqual(environment["FIXTURE_CONTROL"], "retained")
                self.assertEqual(environment["PATH"], os.defpath)
            self.assertEqual(dict(os.environ), caller)


if __name__ == "__main__":
    unittest.main()
