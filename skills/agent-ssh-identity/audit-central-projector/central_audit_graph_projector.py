#!/usr/bin/env python3
"""GPL-3.0-or-later. Central audit graph projector (runs on the projection
host). Reads new scoped SYSCALL records from the shared Atlas
linux_audit.events collection and projects them into the OpenPlanner graph
through the existing SDK container, with acknowledged-delivery semantics.

The configured Atlas credential must be read-only on linux_audit.events
(never the shipper, never the application credential). Records are ordered
ascending by event_epoch; a sliding overlap window re-reads late inserts and
the processed table deduplicates by _id."""
import json
from itertools import islice
import sqlite3
import subprocess
import sys
import time
from pathlib import Path

CONFIG_PATH = '/etc/audit-central-projector/config.json'
STATE_DIR = '/var/lib/audit-central-projector'
INGEST_PATH = '/usr/local/lib/knoxx-audit-forwarder/audit_graph_ingest.mjs'
CONTAINER = 'knoxx-knoxx-backend-1'
AGENT_AUIDS = [str(n) for n in range(1002, 1011)]
BATCH = 250
OVERLAP_SECONDS = 7 * 86400
MAX_LAG_SECONDS = 86400


class State:
    def __init__(self, path):
        Path(path).mkdir(mode=0o700, parents=True, exist_ok=True)
        self.db = sqlite3.connect(str(Path(path) / 'state.sqlite3'), timeout=30)
        self.db.executescript('''
            PRAGMA journal_mode=WAL; PRAGMA synchronous=FULL;
            CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT);
            CREATE TABLE IF NOT EXISTS processed (id TEXT PRIMARY KEY);
        ''')

    def watermark(self):
        row = self.db.execute("SELECT value FROM meta WHERE key='watermark'").fetchone()
        return float(row[0]) if row else time.time() - MAX_LAG_SECONDS

    def seen(self, ids):
        if not ids:
            return set()
        return {row[0] for row in self.db.execute(
            'SELECT id FROM processed WHERE id IN (%s)' % ','.join('?' * len(ids)), ids)}

    def mark(self, ids, watermark):
        with self.db:
            self.db.executemany('INSERT OR IGNORE INTO processed VALUES (?)',
                                [(i,) for i in ids])
            self.db.execute('INSERT OR REPLACE INTO meta VALUES (?,?)',
                            ('watermark', f'{watermark:.3f}'))


def select_unseen(records, state, limit=BATCH):
    """Bound delivery after deduplication, so overlap cannot starve new rows."""
    records = iter(records)
    fresh = []
    while len(fresh) < limit:
        batch = list(islice(records, limit - len(fresh)))
        if not batch:
            break
        seen = state.seen([str(r['_id']) for r in batch])
        fresh.extend(r for r in batch if str(r['_id']) not in seen)
    return fresh


def main():
    from pymongo import MongoClient
    config = json.loads(Path(CONFIG_PATH).read_text())
    state = State(STATE_DIR)
    watermark = state.watermark()
    client = MongoClient(config['uri'], username=config['username'],
                         password=config['password'], authSource='admin',
                         appname='audit-central-projector',
                         serverSelectionTimeoutMS=10000, connectTimeoutMS=10000,
                         socketTimeoutMS=20000)
    events = client['linux_audit'].events
    query = {'type': 'SYSCALL', 'auid': {'$in': AGENT_AUIDS},
             'event_epoch': {'$gte': max(0.0, watermark - OVERLAP_SECONDS)}}
    cursor = events.find(query, {'raw': 0, 'raw_b64': 0},
                         sort=[('event_epoch', 1), ('serial', 1)]
                         ).batch_size(BATCH)
    fresh = select_unseen(cursor, state)
    if not fresh:
        print('graph_delivery_ok batch=0')
        return
    result = subprocess.run(['docker', 'exec', '-i', '--user', 'node', CONTAINER,
                             'node', '--input-type=module',
                             '-e', Path(INGEST_PATH).read_text()],
                            input=json.dumps(fresh), capture_output=True,
                            text=True, timeout=100)
    if result.returncode:
        # Do not log stderr: driver output can carry credentials or raw data.
        print('graph_delivery_failed; nothing acknowledged', file=sys.stderr)
        raise SystemExit(1)
    replies = [json.loads(line) for line in result.stdout.splitlines()
               if line.startswith('{"audit_graph_ack":')]
    if len(replies) != 1 or set(replies[0]['audit_graph_ack']) != \
            {str(r['_id']) for r in fresh}:
        raise SystemExit('graph_delivery_failed invalid acknowledgment')
    new_watermark = max(watermark, max(r.get('event_epoch', watermark) for r in fresh))
    state.mark([str(r['_id']) for r in fresh], new_watermark)
    print(f'graph_delivery_ok batch={len(fresh)} watermark={new_watermark:.3f}')
    client.close()


if __name__ == '__main__':
    main()
