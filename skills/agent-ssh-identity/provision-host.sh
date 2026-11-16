#!/usr/bin/env bash
# GPL-3.0-or-later. Idempotent per-host provisioning of the nine harness agent
# accounts with NOPASSWD sudo and the persistent audit rules mirroring Knoxx.
# Run on the target host, as a user with NOPASSWD root escalation:
#   curl-less install: ssh <host> 'sudo -n bash -s' < provision-host.sh
set -euo pipefail

AGENTS=(codex grokbot opencode hermes pi eta-mu knoxx claude agy)

echo "=== user accounts (UIDs 1002-1010) ==="
uid=1002
for name in "${AGENTS[@]}"; do
    if getent passwd "$name" >/dev/null; then
        actual="$(getent passwd "$name" | cut -d: -f3)"
        if [ "$actual" != "$uid" ]; then
            echo "FATAL: $name exists with UID $actual, expected $uid" >&2
            exit 1
        fi
        usermod -s /bin/bash "$name"
    else
        useradd -m -s /bin/bash -u "$uid" "$name"
    fi
    echo "ok: $name uid=$uid"
    uid=$((uid + 1))
done

echo "=== sudoers ==="
cat > /etc/sudoers.d/90-agent-identities <<'EOF'
codex ALL=(ALL:ALL) NOPASSWD: ALL
grokbot ALL=(ALL:ALL) NOPASSWD: ALL
opencode ALL=(ALL:ALL) NOPASSWD: ALL
hermes ALL=(ALL:ALL) NOPASSWD: ALL
pi ALL=(ALL:ALL) NOPASSWD: ALL
eta-mu ALL=(ALL:ALL) NOPASSWD: ALL
knoxx ALL=(ALL:ALL) NOPASSWD: ALL
claude ALL=(ALL:ALL) NOPASSWD: ALL
agy ALL=(ALL:ALL) NOPASSWD: ALL
EOF
chmod 0440 /etc/sudoers.d/90-agent-identities
visudo -cf /etc/sudoers.d/90-agent-identities

echo "=== auditd ==="
if ! dpkg-query -W -f='${Status}' auditd 2>/dev/null | grep -q 'install ok installed'; then
    DEBIAN_FRONTEND=noninteractive apt-get install -y auditd
fi
systemctl enable --now auditd

echo "=== persistent audit rules ==="
cat > /etc/audit/rules.d/70-agent-identities.rules <<'EOF'
# Exec attribution for the nine harness login identities (b64 + b32).
-a always,exit -F arch=b64 -S execve,execveat -F auid=1002 -k agent_exec
-a always,exit -F arch=b32 -S execve,execveat -F auid=1002 -k agent_exec
-a always,exit -F arch=b64 -S execve,execveat -F auid=1003 -k agent_exec
-a always,exit -F arch=b32 -S execve,execveat -F auid=1003 -k agent_exec
-a always,exit -F arch=b64 -S execve,execveat -F auid=1004 -k agent_exec
-a always,exit -F arch=b32 -S execve,execveat -F auid=1004 -k agent_exec
-a always,exit -F arch=b64 -S execve,execveat -F auid=1005 -k agent_exec
-a always,exit -F arch=b32 -S execve,execveat -F auid=1005 -k agent_exec
-a always,exit -F arch=b64 -S execve,execveat -F auid=1006 -k agent_exec
-a always,exit -F arch=b32 -S execve,execveat -F auid=1006 -k agent_exec
-a always,exit -F arch=b64 -S execve,execveat -F auid=1007 -k agent_exec
-a always,exit -F arch=b32 -S execve,execveat -F auid=1007 -k agent_exec
-a always,exit -F arch=b64 -S execve,execveat -F auid=1008 -k agent_exec
-a always,exit -F arch=b32 -S execve,execveat -F auid=1008 -k agent_exec
-a always,exit -F arch=b64 -S execve,execveat -F auid=1009 -k agent_exec
-a always,exit -F arch=b32 -S execve,execveat -F auid=1009 -k agent_exec
-a always,exit -F arch=b64 -S execve,execveat -F auid=1010 -k agent_exec
-a always,exit -F arch=b32 -S execve,execveat -F auid=1010 -k agent_exec
EOF
cat > /etc/audit/rules.d/75-agent-integrity.rules <<'EOF'
# Agent-origin changes to audit, privilege, SSH and account configuration.
-w /etc/audit/ -p wa -k agent_audit_config
-w /etc/sudoers -p wa -k agent_privilege_config
-w /etc/sudoers.d/ -p wa -k agent_privilege_config
-w /etc/ssh/ -p wa -k agent_ssh_config
-w /etc/passwd -p wa -k agent_accounts
-w /etc/shadow -p wa -k agent_accounts
-w /etc/group -p wa -k agent_accounts
-a always,exit -F arch=b64 -F dir=/var/log/ -F perm=wa -F auid>=1002 -F auid<=1010 -k agent_log_change
-a always,exit -F arch=b32 -F dir=/var/log/ -F perm=wa -F auid>=1002 -F auid<=1010 -k agent_log_change
EOF
chmod 0640 /etc/audit/rules.d/70-agent-identities.rules /etc/audit/rules.d/75-agent-integrity.rules
augenrules --load

echo "=== verification ==="
grep -q 'pam_loginuid.so' /etc/pam.d/sshd || { echo 'FATAL: pam_loginuid missing from sshd PAM' >&2; exit 1; }
count="$(auditctl -l | grep -c 'auid=100[2-9]\|auid=1010' || true)"
[ "$count" -ge 18 ] && echo "audit rules loaded: agent_exec per-auid rules visible via auditctl"
systemctl is-active auditd
echo "PROVISION_OK host=$(hostname)"
