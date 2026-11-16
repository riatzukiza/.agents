"""GPL-3.0-or-later. Isolated checks; no network or real credentials."""
import ast
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from central_audit_graph_projector import BATCH, State, select_unseen


class CentralProjectorTests(unittest.TestCase):
    def test_overlap_does_not_starve_backlog(self):
        rows = [{'_id': str(i), 'event_epoch': float(i)} for i in range(851)]
        with tempfile.TemporaryDirectory() as directory:
            state = State(directory)
            try:
                state.mark([str(i) for i in range(BATCH)], BATCH - 1)
                sizes = []
                for _ in range(5):
                    fresh = select_unseen(iter(rows), state)
                    if not fresh:
                        break
                    sizes.append(len(fresh))
                    state.mark([r['_id'] for r in fresh],
                               max(r['event_epoch'] for r in fresh))
                self.assertEqual(sizes, [250, 250, 101])
                self.assertEqual(len(state.seen([r['_id'] for r in rows])), 851)
            finally:
                state.db.close()

    def test_restart_and_late_arrival(self):
        with tempfile.TemporaryDirectory() as directory:
            state = State(directory)
            state.mark(['old'], 100)
            state.db.close()
            state = State(directory)
            try:
                late = {'_id': 'late', 'event_epoch': 50}
                rows = [{'_id': 'old', 'event_epoch': 1}, late]
                self.assertEqual(select_unseen(iter(rows), state), [late])
                self.assertEqual(state.watermark(), 100)
            finally:
                state.db.close()

    def test_installer_remote_payload(self):
        source = Path(__file__).with_name('install-central-credential.py')
        tree = ast.parse(source.read_text())
        remote = next(node.value.value for node in tree.body
                      if isinstance(node, ast.Assign) and any(
                          isinstance(target, ast.Name) and target.id == 'remote'
                          for target in node.targets))
        config = {'uri': 'mongodb+srv://cluster0.abpqhln.mongodb.net/?tls=true',
                  'username': 'central_audit_reader', 'password': 'test-only'}
        with tempfile.TemporaryDirectory() as directory:
            output = io.StringIO()
            with patch('os.geteuid', return_value=0), \
                    patch('sys.stdin', io.StringIO(json.dumps(config))), \
                    contextlib.redirect_stdout(output):
                exec(remote.replace('/etc/audit-central-projector', directory), {})
            destination = Path(directory) / 'config.json'
            self.assertEqual(json.loads(destination.read_text()), config)
            self.assertEqual(destination.stat().st_mode & 0o777, 0o600)
            self.assertEqual(list(Path(directory).iterdir()), [destination])
            self.assertNotIn(config['password'], output.getvalue())


if __name__ == '__main__':
    unittest.main()
