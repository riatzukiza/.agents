#!/usr/bin/env bash
# GPL-3.0-or-later. Prepare the forwarding environment on a target host:
# shipper system user, venv with pymongo, script copies and the service unit.
# Run, as a user with NOPASSWD sudo:  ssh <host> 'sudo -n bash -s' < provision-forwarder-env.sh <dist-dir>
# The distribution directory (with audit_forwarder.py and the service file)
# is piped or staged at /usr/local/lib/audit-forwarder beforehand.
set -euo pipefail

DIST="${1:-.}"

/usr/sbin/useradd --system --shell /usr/sbin/nologin --home-dir /nonexistent audit-shipper 2>/dev/null || true
/usr/sbin/usermod -aG adm audit-shipper

install -d -m 0755 -o root -g root /usr/local/lib/audit-forwarder
[ -f /usr/local/lib/audit-forwarder/audit_forwarder.py ] || \
    echo "NOTE: stage audit_forwarder.py into /usr/local/lib/audit-forwarder"
[ -d /usr/local/lib/audit-forwarder/venv ] || \
    python3 -m venv /usr/local/lib/audit-forwarder/venv
/usr/local/lib/audit-forwarder/venv/bin/pip install --quiet 'pymongo>=4.6,<5'

install -d -m 0750 -o root -g audit-shipper /etc/audit-forwarder
install -d -m 0700 -o audit-shipper -g audit-shipper /var/lib/audit-forwarder

if [ -f "$DIST/linux-audit-forwarder.service" ]; then
    install -m 0644 -o root -g root "$DIST/linux-audit-forwarder.service" /etc/systemd/system/linux-audit-forwarder.service
fi
systemctl daemon-reload
echo "FORWARDER_ENV_OK host=$(hostname)"
