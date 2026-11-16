#!/usr/bin/env bash
set -euo pipefail

# send-to-inbox.sh <actor-id> <message-file>
# Drops a message into an actor's inbox, stamping a fresh id.

ACTOR_ID="${1:-}"
MSG_FILE="${2:-}"

if [[ -z "$ACTOR_ID" || -z "$MSG_FILE" ]]; then
  echo "Usage: $0 <actor-id> <message-file>" >&2
  exit 1
fi

WORKSPACE_ROOT="$(pwd)"
while [[ "$WORKSPACE_ROOT" != "/" && ! -d "$WORKSPACE_ROOT/.eta-mu/actors" ]]; do
  WORKSPACE_ROOT="$(dirname "$WORKSPACE_ROOT")"
done
if [[ "$WORKSPACE_ROOT" == "/" ]]; then
  echo "Could not find workspace root containing .eta-mu/actors/" >&2
  exit 1
fi

if [[ ! -f "$MSG_FILE" ]]; then
  echo "Message file not found: $MSG_FILE" >&2
  exit 1
fi

ACTOR_DIR="$WORKSPACE_ROOT/.eta-mu/actors/$ACTOR_ID"
if [[ ! -d "$ACTOR_DIR" ]]; then
  echo "Actor not found: $ACTOR_DIR" >&2
  exit 1
fi

TS=$(date -u +%Y-%m-%dT%H-%M-%S)
UUID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
DEST="$ACTOR_DIR/inbox/$TS-$UUID.md"

cp "$MSG_FILE" "$DEST"
echo "Sent to $ACTOR_ID inbox: $DEST"
