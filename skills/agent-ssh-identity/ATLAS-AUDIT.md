# Atlas audit forwarding

GPL-3.0-or-later. Operational scope: Knoxx Linux audit records to the existing
Atlas Project 0 / Cluster0, collection `linux_audit.events`.

## Access contract

Custom role `knoxx_audit_insert_only` grants only `insert` on
`linux_audit.events`. Database user `knoxx_audit_shipper` must have only that
role on `admin`, scoped to `Cluster0`. No read, update, remove, create-index,
drop, database administration, or other built-in roles belong on this user.
Collection creation, indexes, inspection and retention are administrator tasks.
The shipper uses no Atlas administrative API credentials.

The source host's public /32 is already on the Atlas access list. TLS and
normal certificate/hostname verification are required. Credential provisioning
was completed by the user in the Atlas console, followed by the hidden-input
`install_audit_credential.py` helper. The password crosses SSH stdin into the
protected host configuration without appearing in source files or CLI arguments.

## Host installation

- Source: this directory's `audit_forwarder.py` and service definition.
- Runtime: `/usr/local/lib/knoxx-audit-forwarder/`, root-owned, including a
  dedicated Python venv with PyMongo 4.19.0 and dnspython 2.9.0.
- Identity: `knoxx-audit-shipper`, system account with nologin shell; private
  primary group, supplementary `adm` for audit-log reads, no sudo.
- Configuration: `/etc/knoxx-audit-forwarder/config.json`, root-owned with
  group `knoxx-audit-shipper`, mode 0640, directory 0750. Fields: `uri`,
  `username`, `password`, `source` (hostname plus machine-id).
- Persistent queue and cursors: `/var/lib/knoxx-audit-forwarder/`, mode 0700.
- Unit: `knoxx-audit-forwarder.service`, hardened, no capabilities, read-only
  host filesystem except the service's own state directory.

## Delivery semantics

The service scans current and numerically rotated uncompressed audit files.
Complete lines and file offsets enter one durable SQLite transaction. Partial
lines wait for completion. A batch leaves the queue only after acknowledged
majority insertion or confirmed duplicate `_id` errors. Stable SHA-256 IDs
include source identity and exact line bytes, allowing retries without editing
existing Atlas records. Non-duplicate write errors retain the batch.

Each document preserves `raw_b64` (exact original line bytes), readable `raw`,
source, audit serial and source timestamp, plus fields such as `auid`, `uid`,
`euid`, `ses`, `exe`, `key`, and record type when present. Multi-line audit
events are separate documents joined by source and serial/timestamp. A source
heartbeat is queued every minute. Source timestamps and heartbeat claims are
not independent collector attestation.

The queue buffers during network outages; audit rotation can still lose lines
if files disappear before the local collector reads them. Queue disk exhaustion
and stopped shipping require operational monitoring. Truncation is reported.
No automatic record expiration, TTL index, deletion, or heartbeat alert is
configured. The existing Flex cluster has finite capacity: choose retention
deliberately after measuring volume. Audit command arguments may contain
sensitive values; use normal restricted Atlas reader permissions.

## Operations

```sh
sudo systemctl status knoxx-audit-forwarder
sudo journalctl -u knoxx-audit-forwarder -n 30 --no-pager
sudo auditctl -s
```

Inspect records using a separate authorized reader, never by adding read rights
to the shipper. Query `kind=linux_audit` with `auid` equal to the numeric harness
UID represented as a string. The `source_auid_event_epoch` index supports this.
Inspect `kind=shipper_heartbeat` for recent source liveness. Verify the local
pending row count during outages and after recovery. Do not print config.json.

Tests: `venv/bin/python -m unittest -v test_audit_forwarder` from the installed
directory. Tests cover partial lines, rotation, cursor persistence, exact byte
preservation, connection failure/retry, permission errors, and duplicate replay.
Live verification must additionally demonstrate remote arrival and authorization denial (code 13, or Atlas code 8000 with an explicit
not-allowed action message) for read, update and delete using the shipping credential.

## Security boundary

Full Docker and sudo remain enabled by user choice. Root can stop the collector,
alter local records before shipping, or inject false records. The shipper's
insert-only credential cannot edit received records, but Atlas administrators
still can. No immutable audit configuration or tamper-proof guarantee is made.

Historical observation during setup: existing user `originalerror502_db_user` has atlasAdmin,
and that username occurs in the Knoxx backend container environment. Full host
Docker/root access may therefore expose a separate credential that can modify
the audit database. Narrow that application account separately, or isolate audit
storage in another Atlas project without administrator credentials on Knoxx.
The existing application account is not changed by this installation.

## Live verification — 2026-10-22 America/Chicago

Service enabled and active. Shipping user listing confirms only
knoxx_audit_insert_only on admin, scoped to Cluster0. Configuration mode 0640
root:knoxx-audit-shipper verified without printing credential.

Using that exact credential from the unprivileged service account:
- insert into linux_audit.events succeeded; probe retained as evidence;
- find, update and delete denied with explicit Atlas authorization error8000;
- insert into linux_audit.not_authorized denied with error8000.

Separate authorized Atlas reader confirmed historical SYSCALL records with
auid1002/euid0 and fresh EXECVE marker audit-forwarding-live-probe-20261022,
including records for sudo and its root printf child. Backlog drained to zero;
service delivery_ok pending0 recorded. Kernel audit lost0. Three durability
tests passed earlier. No retention expiry or stopped-shipping alert configured.

## Permission refinement — 2026-10-22

The previously identified backend atlasAdmin exposure is resolved: the shared
application account now has readWrite only on openplanner, knoxx_dev and calliope.
A fresh backend connection verified denied find/update/delete on linux_audit,
positive application CRUD, SDK startup and health200. The two non-production
application DBs retain compatibility access, not a claim that production uses
them. No user administration or all-database role remains. See AUDIT-GRAPH.md
for the continuous graph projection and its raw-evidence provenance boundary.
