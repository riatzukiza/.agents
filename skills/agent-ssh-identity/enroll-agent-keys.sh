#!/usr/bin/env bash
# GPL-3.0-or-later. Enroll harness agent public keys into the matching local
# agent account. Idempotent. stdin: lines "harness<TAB>authorized_key_line"
# Run on the target host as root:  sudo -n bash enroll-agent-keys.sh < keys.tsv
set -euo pipefail

AGENTS=(codex grokbot opencode hermes pi eta-mu knoxx claude agy)

while IFS=$'\t' read -r harness key; do
    [ -n "$harness" ] || continue
    case " ${AGENTS[*]} " in *" $harness "*) ;; *) echo "skip unknown harness $harness" >&2; continue;; esac
    uid="$(getent passwd "$harness" | cut -d: -f3)"
    home="$(getent passwd "$harness" | cut -d: -f6)"
    install -d -m 0700 -o "$harness" -g "$harness" "$home/.ssh"
    touch "$home/.ssh/authorized_keys"
    if grep -qxF "$key" "$home/.ssh/authorized_keys"; then
        echo "present: $harness"
    else
        echo "$key" >> "$home/.ssh/authorized_keys"
        echo "enrolled: $harness"
    fi
    chown "$harness:$harness" "$home/.ssh/authorized_keys"
    chmod 0600 "$home/.ssh/authorized_keys"
done
