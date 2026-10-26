"""GPL-3.0-or-later. Verify durability and acknowledgment semantics."""
import base64
import tempfile
import unittest
from pathlib import Path
from pymongo.errors import BulkWriteError, ConnectionFailure
from audit_forwarder import Spool


class Destination:
    def __init__(self, failure=None):
        self.failure = failure
        self.docs = []

    def insert_many(self, docs, ordered):
        assert ordered is False
        self.docs.extend(docs)
        if self.failure:
            raise self.failure


class Durability(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.logs = self.root / 'logs'
        self.logs.mkdir()
        self.line = b'type=SYSCALL msg=audit(1792723782.602:2405): auid=1002 uid=0 euid=0 key="agent_exec"\n'
        self.file = self.logs / 'audit.log'
        self.file.write_bytes(self.line + b'partial')
        self.spool = Spool(self.root / 'state', self.logs, 'test-source')

    def tearDown(self):
        self.spool.db.close()
        self.temp.cleanup()

    def count(self):
        return self.spool.db.execute('SELECT count(*) FROM pending').fetchone()[0]

    def test_rotation_partial_line_and_restart(self):
        self.spool.collect()
        self.assertEqual(self.count(), 2)  # audit record + heartbeat
        self.assertEqual(self.spool.db.execute('SELECT offset FROM cursors').fetchone()[0], len(self.line))
        self.spool.collect()
        self.assertEqual(self.count(), 2)
        self.file.rename(self.logs / 'audit.log.1')
        self.file.write_bytes(self.line.replace(b'2405', b'2406'))
        self.spool.collect()
        self.assertEqual(self.count(), 3)
        self.spool.db.close()
        self.spool = Spool(self.root / 'state', self.logs, 'test-source')
        self.spool.collect()
        self.assertEqual(self.count(), 3)
        self.assertEqual(base64.b64decode(self.spool.document(self.line)['raw_b64']), self.line)

    def test_failure_retains_spool_then_replay_acknowledges(self):
        self.spool.collect()
        with self.assertRaises(ConnectionFailure):
            self.spool.flush(Destination(ConnectionFailure('offline')))
        self.assertEqual(self.count(), 2)
        self.assertEqual(self.spool.db.execute('SELECT count(*) FROM graph_pending').fetchone()[0], 0)
        self.assertEqual(self.spool.flush(Destination()), 2)
        self.assertEqual(self.count(), 0)
        self.assertEqual(self.spool.db.execute('SELECT count(*) FROM graph_pending').fetchone()[0], 1)

    def test_only_duplicate_failures_are_acknowledged(self):
        self.spool.collect()
        denied = BulkWriteError({'writeErrors': [{'code': 13}], 'writeConcernErrors': []})
        with self.assertRaises(BulkWriteError):
            self.spool.flush(Destination(denied))
        self.assertEqual(self.count(), 2)
        duplicate = BulkWriteError({'writeErrors': [{'code': 11000}], 'writeConcernErrors': []})
        self.assertEqual(self.spool.flush(Destination(duplicate)), 2)
        self.assertEqual(self.count(), 0)
        self.assertEqual(self.spool.db.execute('SELECT count(*) FROM graph_pending').fetchone()[0], 1)


if __name__ == '__main__':
    unittest.main()
