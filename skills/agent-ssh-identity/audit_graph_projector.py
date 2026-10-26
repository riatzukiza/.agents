#!/usr/bin/python3
"""GPL-3.0-or-later. Project acknowledged audit observations through OpenPlanner SDK."""
import json
import sqlite3
import subprocess
import sys


def main():
    db = sqlite3.connect('/var/lib/knoxx-audit-forwarder/spool.sqlite3', timeout=30)
    rows = db.execute('SELECT id,payload FROM graph_pending ORDER BY rowid LIMIT 250').fetchall()
    if not rows:
        print('graph_delivery_ok batch=0')
        return
    # Execute as the existing unprivileged application user. The container's
    # Mongo credential has application DB access only, never audit DB access.
    result = subprocess.run(['docker', 'exec', '-i', '--user', 'node',
                             'knoxx-knoxx-backend-1', 'node', '--input-type=module',
                             '-e', open('/usr/local/lib/knoxx-audit-forwarder/audit_graph_ingest.mjs').read()],
                            input=json.dumps([json.loads(payload) for _, payload in rows]),
                            capture_output=True, text=True, timeout=100)
    if result.returncode:
        print('graph_delivery_failed; outbox retained', file=sys.stderr)
        raise SystemExit(1)
    # SDK output must explicitly acknowledge the full source batch. Do not log
    # stderr, raw records or Mongo driver errors that might contain secrets.
    replies = [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{"audit_graph_ack":')]
    if len(replies) != 1 or set(replies[0]['audit_graph_ack']) != {row[0] for row in rows}:
        raise SystemExit('graph_delivery_failed invalid acknowledgment; outbox retained')
    with db:
        db.executemany('DELETE FROM graph_pending WHERE id=?', [(row[0],) for row in rows])
    pending = db.execute('SELECT count(*) FROM graph_pending').fetchone()[0]
    print(f'graph_delivery_ok batch={len(rows)} pending={pending}')
    db.close()


if __name__ == '__main__':
    main()
