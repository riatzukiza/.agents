#!/usr/bin/python3
"""GPL-3.0-or-later. Durable, insert-only forwarding of raw Linux audit lines."""
import base64
import datetime as dt
import glob
import hashlib
import json
import logging
import os
import re
import signal
import sqlite3
import time
import uuid
from pathlib import Path

STOP = False


def stop(*_):
    global STOP
    STOP = True


class Spool:
    def __init__(self, state, logs, source):
        self.logs, self.source = Path(logs), source
        Path(state).mkdir(mode=0o700, parents=True, exist_ok=True)
        self.db = sqlite3.connect(str(Path(state) / 'spool.sqlite3'))
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.execute('PRAGMA synchronous=FULL')
        self.db.executescript('''
        CREATE TABLE IF NOT EXISTS cursors (identity TEXT PRIMARY KEY, offset INTEGER);
        CREATE TABLE IF NOT EXISTS pending (id TEXT PRIMARY KEY, payload TEXT);
        CREATE TABLE IF NOT EXISTS graph_pending (id TEXT PRIMARY KEY, payload TEXT);
        CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT);
        ''')

    def document(self, raw):
        text = raw.decode('utf-8', errors='replace')
        doc = {'_id': hashlib.sha256(self.source.encode() + b'\0' + raw).hexdigest(),
               'source': self.source, 'kind': 'linux_audit',
               'raw': text.rstrip('\n'), 'raw_b64': base64.b64encode(raw).decode()}
        match = re.search(r'msg=audit\((\d+(?:\.\d+)?):(\d+)\)', text)
        if match:
            doc['event_epoch'] = float(match[1])
            doc['serial'] = int(match[2])
        for key in ('type', 'auid', 'uid', 'euid', 'ses', 'key', 'exe', 'comm'):
            match = re.search(r'(?:^|\s)' + key + r'=("[^"]*"|[^\s\x1d]+)', text)
            if match:
                doc[key] = match[1].strip('"')
        return doc

    def collect(self, budget=8 * 1024 * 1024):
        files = []
        for name in glob.glob(str(self.logs / 'audit.log*')):
            if re.search(r'/audit\.log(?:\.\d+)?$', name):
                try:
                    files.append((os.stat(name).st_mtime_ns, name))
                except FileNotFoundError:
                    pass
        total = 0
        for _, name in sorted(files):
            try:
                stream = open(name, 'rb')
            except FileNotFoundError:
                continue
            with stream, self.db:
                st = os.fstat(stream.fileno())
                identity = f'{st.st_dev}:{st.st_ino}'
                old = self.db.execute('SELECT offset FROM cursors WHERE identity=?', (identity,)).fetchone()
                offset = old[0] if old else 0
                if st.st_size < offset:
                    logging.error('AUDIT_FILE_TRUNCATED inode=%s previous=%d size=%d', identity, offset, st.st_size)
                    offset = 0
                stream.seek(offset)
                while total < budget:
                    raw = stream.readline()
                    if not raw or not raw.endswith(b'\n'):
                        break  # Never acknowledge a partial line.
                    doc = self.document(raw)
                    self.db.execute('INSERT OR IGNORE INTO pending VALUES (?,?)',
                                    (doc['_id'], json.dumps(doc)))
                    offset = stream.tell()
                    total += len(raw)
                self.db.execute('INSERT OR REPLACE INTO cursors VALUES (?,?)', (identity, offset))
            if total >= budget:
                break
        with self.db:
            old = self.db.execute("SELECT value FROM meta WHERE key='heartbeat'").fetchone()
            now = time.time()
            if not old or now - float(old[0]) >= 60:
                doc = {'_id': 'heartbeat:' + str(uuid.uuid4()), 'kind': 'shipper_heartbeat',
                       'source': self.source, 'source_epoch': now,
                       'boot_id': Path('/proc/sys/kernel/random/boot_id').read_text().strip()}
                self.db.execute('INSERT INTO pending VALUES (?,?)', (doc['_id'], json.dumps(doc)))
                self.db.execute("INSERT OR REPLACE INTO meta VALUES ('heartbeat',?)", (str(now),))
        return total

    def flush(self, collection):
        from pymongo.errors import BulkWriteError
        rows = self.db.execute('SELECT id,payload FROM pending ORDER BY rowid LIMIT 500').fetchall()
        if not rows:
            return 0
        docs = [json.loads(payload) for _, payload in rows]
        try:
            collection.insert_many(docs, ordered=False)
        except BulkWriteError as exc:
            # Duplicate IDs mean a prior delivery was acknowledged remotely but
            # not locally (for example a crash between insert and spool commit).
            details = exc.details
            if details.get('writeConcernErrors') or any(e.get('code') != 11000 for e in details.get('writeErrors', [])):
                raise
        with self.db:
            for doc in docs:
                if doc.get('type') == 'SYSCALL' and doc.get('auid') in {str(n) for n in range(1002, 1011)}:
                    self.db.execute('INSERT OR IGNORE INTO graph_pending VALUES (?,?)',
                                    (doc['_id'], json.dumps(doc)))
            self.db.executemany('DELETE FROM pending WHERE id=?', [(row[0],) for row in rows])
        return len(rows)


def run():
    from pymongo import MongoClient
    from pymongo.write_concern import WriteConcern
    logging.basicConfig(level=logging.INFO, format='%(levelname)s %(message)s')
    # Deployment-specific paths are overridable so the same file serves Knoxx
    # (original installation) and every later host (linux-audit-forwarder
    # service sets both variables). Defaults keep the Knoxx deployment intact.
    config_path = os.environ.get('AUDIT_FORWARDER_CONFIG',
                                 '/etc/knoxx-audit-forwarder/config.json')
    state_path = os.environ.get('AUDIT_FORWARDER_STATE',
                                '/var/lib/knoxx-audit-forwarder')
    config = json.loads(Path(config_path).read_text())
    source = config['source']
    appname = source.split(':')[0] + '-audit-forwarder'
    spool = Spool(state_path, '/var/log/audit', source)
    client = MongoClient(config['uri'], username=config['username'], password=config['password'],
                         authSource='admin', appname=appname,
                         retryWrites=True, serverSelectionTimeoutMS=10000, connectTimeoutMS=10000,
                         socketTimeoutMS=20000)
    collection = client['linux_audit'].get_collection('events', write_concern=WriteConcern(w='majority', wtimeout=15000))
    last_report, failures = 0, 0
    while not STOP:
        started = time.monotonic()
        try:
            spool.collect()
            sent = spool.flush(collection)
            failures = 0
            if time.monotonic() - last_report >= 60:
                pending = spool.db.execute('SELECT count(*) FROM pending').fetchone()[0]
                logging.info('delivery_ok batch=%d pending=%d', sent, pending)
                last_report = time.monotonic()
        except Exception as exc:
            failures += 1
            # Do not print exception messages: driver errors can contain URIs,
            # raw records or other credentials/data.
            logging.error('delivery_failed error_type=%s consecutive=%d; spool retained', type(exc).__name__, failures)
        delay = min(30, 2 ** min(failures, 5)) if failures else (0.05 if sent == 500 else 2)
        while not STOP and time.monotonic() - started < delay:
            time.sleep(0.2)
    client.close()
    spool.db.close()


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    run()
