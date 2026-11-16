# Linux audit evidence in the knowledge graph

GPL-3.0-or-later. Owner: agent-ssh-identity operational integration.

## Source and projection

Authoritative received records remain in Atlas `Cluster0/linux_audit.events`.
Their IDs hash the source identity and exact original line bytes. Graph data is
a disposable projection in the production backend's existing `openplanner`
database, project/lake `linux-audit`. Graph projections do not replace the raw
records and can be modified by application writers without changing evidence.

The raw forwarder commits a `graph_pending` SQLite row only after successful
Atlas insertion or confirmed duplicate delivery. It does not gain Atlas read or
graph-write permissions. `knoxx-audit-graph.timer` drains this durable outbox
through `knoxx-audit-graph.service` using the installed OpenPlanner SDK inside
the existing backend container. It runs Node as the container's `node` user and
uses the backend's existing application-scoped Mongo credential. No application
image or source is patched; no new Atlas credential is introduced.

The timer's host process requires Docker access and therefore runs as root.
This is a privileged administrative projector, not a new isolation boundary.
Its root-owned runtime files live under `/usr/local/lib/knoxx-audit-forwarder`.
It has no need to read or modify the Atlas audit collection. The backend Mongo
credential must remain denied access to `linux_audit`.

## Shape

Each agent-origin SYSCALL record yields typed native `graph.node`/`graph.edge`
events through SDK admission and projected rows in `graph_edges`:

- `audit.syscall`: recorded login UID, effective UID, session, executable path,
  source timestamp and serial; links to exact raw Atlas document ID.
- `audit.host`: source hostname plus machine-id.
- `audit.linux_principal`: UID within that host identity.
- `audit.executable_path`: observed process executable path, not a file-content
  identity or a claim about what an executable intended or accomplished.
- `audit.harness`: codex, grokbot, opencode, hermes, pi, eta-mu, knoxx, claude,
  or agy, mapped from the verified UID registry in `identities.tsv`.

Relations: `observed_on`, `login_attributed_to`, `executed_as`, `executed_path`,
and `mapped_to_harness`. Raw observations are marked observed; UID-to-harness
mapping is derived and names its registry basis. Host root can forge or stop
source logging; these tiers describe recorded evidence, not tamper-proof truth.

Command arguments and raw audit text are excluded from graph text. Raw audit
records remain available to a separately authorized reader. No automatic
embeddings are requested: this is a structural graph projection, not a claim
that semantic vectors exist. Native node labels and typed edges are queryable
in their normal graph collections.

## Compatibility boundary

The installed SDK builds graph edge IDs in `_id`; Knoxx translation graph code
also requires unique `id` in the shared graph_edges collection. An audit-only
collection adapter mirrors each SDK-generated `_id` into `id` before writing.
It preserves the SDK's identity and all existing indexes, rejects foreign
sources, and does not rewrite translation edges. This is a compatibility seam,
not a change to the SDK's admission rules. A failed first attempt admitted some
node/edge events but retained source outbox rows; retries repair the projections.

## Operations and verification

```sh
sudo systemctl status knoxx-audit-graph.timer
sudo journalctl -u knoxx-audit-graph -n 12 --no-pager
sudo python3 -c 'import sqlite3; c=sqlite3.connect("/var/lib/knoxx-audit-forwarder/spool.sqlite3"); print(c.execute("select count(*) from graph_pending").fetchone()[0])'
```

The timer is serialized by systemd and retries retained batches. Destination
events are checked by deterministic event ID; SDK edge projections are repaired
even when a previous attempt admitted an event before failing. Source rows are
removed only after complete destination event coverage and edge write success.

Query `openplanner.events` for `source=linux-audit, kind=graph.node`, and
`openplanner.graph_edges` for `source=linux-audit`. Follow
`extra.provenance.evidence` to Cluster0/linux_audit.events by raw document ID.
Read the node's `extra.epistemic_tier` and edge's `layer` separately.

Historical replay resets only local source-file offsets after a SQLite backup;
the shipper re-inserts identical raw IDs, confirms duplicates, and queues graph
observations. It never rewrites audit files or existing Atlas evidence.

Run `node audit_graph_ingest.mjs --test` for deterministic identity, input scope,
argument exclusion and edge-ID adapter checks. Run the existing Python
forwarder tests for queue gating on Atlas acknowledgment and duplicate recovery.

Known pre-existing consumer gap: the public Knoxx graph connector reports
OpenPlanner not configured; production `/api/graph/export` returned 502 before
these changes. The direct-Mongo client still delegates graph queries to REST.
This integration persists the native graph and verifies it through an independent
Atlas reader; it does not claim those legacy routes or actor graph tools work.

## Backend permission boundary

Existing shared user `originalerror502_db_user` now has only readWrite on
openplanner, knoxx_dev, and calliope; atlasAdmin was removed. Production Knoxx
was verified using openplanner. The two existing non-production/content DBs
retain compatibility access because their consumers were not fully accounted
for; their existence alone is not evidence the production backend needs them.
Separate credentials per application/environment remain a future refinement.

Fresh backend connections verified CRUD on the three allowed DBs, SDK startup
and index creation, health200, and denied audit reads/updates/deletes. No
application container was restarted. App/API credentials and passwords were not
changed. All inspected production containers remained healthy.
