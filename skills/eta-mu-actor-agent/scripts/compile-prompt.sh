#!/usr/bin/env bash
set -euo pipefail

# compile-prompt.sh <actor-id>
# Compiles AGENT.md from actor.edn + goals/ + methods/ + responsibilities/ + schedules/ + triggers/.

ACTOR_ID="${1:-}"
if [[ -z "$ACTOR_ID" ]]; then
  echo "Usage: $0 <actor-id>" >&2
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

ACTOR_DIR="$WORKSPACE_ROOT/.eta-mu/actors/$ACTOR_ID"
if [[ ! -d "$ACTOR_DIR" ]]; then
  echo "Actor not found: $ACTOR_DIR" >&2
  exit 1
fi

OUT="$ACTOR_DIR/AGENT.md"
{
  echo "---"
  PURPOSE=$(grep -E '^ :actor/purpose' "$ACTOR_DIR/actor.edn" | sed 's/.*"\(.*\)".*/\1/')
  # Escape double quotes for YAML frontmatter.
  PURPOSE=${PURPOSE//"/\\"}
  echo "description: \"$PURPOSE\""
  echo "mode: all"
  echo "---"
  echo
  echo "# Actor: $ACTOR_ID"
  echo
  echo "## Identity"
  echo
  echo '```edn'
  cat "$ACTOR_DIR/actor.edn"
  echo '```'
  echo

  for section in goals methods responsibilities schedules triggers; do
    echo "## ${section^}"
    echo
    shopt -s nullglob
    files=("$ACTOR_DIR/$section"/*.md)
    shopt -u nullglob
    if [[ ${#files[@]} -eq 0 ]]; then
      echo "_(no ${section} files yet)_"
      echo
      continue
    fi
    for f in "${files[@]}"; do
      # Skip READMEs in compilation; they are human docs.
      if [[ "$(basename "$f")" == "README.md" ]]; then continue; fi
      echo "### $(basename "$f")"
      echo
      cat "$f"
      echo
    done
  done

  echo "## Runtime"
  echo
  echo "This actor is backgrounded via the mechanism documented in its runtime/ folder."
  echo "Inspect \`$ACTOR_DIR/runtime/\` and \`actor.edn :actor/runtime\` to discover the current runner."
  echo
  echo "## Inbox/Outbox"
  echo
  echo "- Inbox: \`$ACTOR_DIR/inbox/\`"
  echo "- Outbox: \`$ACTOR_DIR/outbox/\`"
  echo "- Sessions: \`$ACTOR_DIR/sessions/\`"
  echo
  echo "## Actor model contract"
  echo
  echo "You are an actor in the .eta-mu/actors/ system. You are equal to every other actor."
  echo "You have a purpose encoded in this prompt, a mailbox at inbox/, and an outbox at outbox/."
  echo "You may read messages from your inbox, write messages to your outbox, and send messages to other actors by dropping files into their inbox/."
  echo "You may also edit the prompt files in goals/, methods/, and responsibilities/ of any actor (including yourself) when the user or another actor asks you to."
  echo "Record every activation as a session under sessions/."
} > "$OUT"

echo "Compiled $OUT"
