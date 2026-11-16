#!/usr/bin/env bash
set -euo pipefail

# actor-status.sh [actor-id]
# Shows actor status: sessions, inbox/outbox counts, runtime.

WORKSPACE_ROOT="$(pwd)"
while [[ "$WORKSPACE_ROOT" != "/" && ! -d "$WORKSPACE_ROOT/.eta-mu/actors" ]]; do
  WORKSPACE_ROOT="$(dirname "$WORKSPACE_ROOT")"
done
if [[ "$WORKSPACE_ROOT" == "/" ]]; then
  echo "Could not find workspace root containing .eta-mu/actors/" >&2
  exit 1
fi

if [[ $# -eq 0 ]]; then
  echo "Available actors under $WORKSPACE_ROOT/.eta-mu/actors/:"
  for d in "$WORKSPACE_ROOT/.eta-mu/actors"/*/; do
    [[ -d "$d" ]] || continue
    id=$(basename "$d")
    sessions=$(find "$d/sessions" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | wc -l)
    inbox=$(find "$d/inbox" -maxdepth 1 -type f 2>/dev/null | wc -l)
    outbox=$(find "$d/outbox" -maxdepth 1 -type f 2>/dev/null | wc -l)
    runtime=$(grep -E '^ :actor/runtime' "$d/actor.edn" 2>/dev/null | sed 's/.*:type :\([^ }]*\).*/\1/' || echo "unknown")
    printf "  %-30s runtime=%-12s sessions=%3s inbox=%3s outbox=%3s\n" "$id" "$runtime" "$sessions" "$inbox" "$outbox"
  done
  exit 0
fi

ACTOR_ID="$1"
ACTOR_DIR="$WORKSPACE_ROOT/.eta-mu/actors/$ACTOR_ID"
if [[ ! -d "$ACTOR_DIR" ]]; then
  echo "Actor not found: $ACTOR_DIR" >&2
  exit 1
fi

echo "Actor: $ACTOR_ID"
echo "Directory: $ACTOR_DIR"
echo
echo "actor.edn:"
cat "$ACTOR_DIR/actor.edn"
echo
echo "Sessions:"
find "$ACTOR_DIR/sessions" -maxdepth 1 -mindepth 1 -type d | sort | while read -r s; do
  id=$(basename "$s")
  status=$(grep -E ':session/status' "$s/session.edn" 2>/dev/null | sed 's/.*:session\/status :\([^ ]*\).*/\1/' || echo "unknown")
  oc=$(grep -E ':session/opencode-session-id' "$s/session.edn" 2>/dev/null | sed 's/.*:session\/opencode-session-id "\([^"]*\)".*/\1/' || echo "unknown")
  printf "  %s  status=%-12s oc=%s\n" "$id" "$status" "$oc"
done
echo
echo "Inbox ($(find "$ACTOR_DIR/inbox" -maxdepth 1 -type f 2>/dev/null | wc -l)) files:"
find "$ACTOR_DIR/inbox" -maxdepth 1 -type f 2>/dev/null | sort | head -20

echo
echo "Outbox ($(find "$ACTOR_DIR/outbox" -maxdepth 1 -type f 2>/dev/null | wc -l)) files:"
find "$ACTOR_DIR/outbox" -maxdepth 1 -type f 2>/dev/null | sort | head -20
