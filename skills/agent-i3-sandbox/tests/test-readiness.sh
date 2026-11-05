#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
tmp="$(mktemp -d)"
sleep 30 & daemon=$!
cleanup() { kill "$daemon" "${writer:-}" 2>/dev/null || true; rm -rf "$tmp"; }
trap cleanup EXIT
cat > "$tmp/emacsclient" <<'CLIENT'
#!/usr/bin/env bash
[[ -f "$READY_FILE" ]]
CLIENT
chmod +x "$tmp/emacsclient"
export PATH="$tmp:$PATH" READY_FILE="$tmp/ready"
(sleep 6; touch "$READY_FILE") & writer=$!
if bash "$root/scripts/wait-emacs" test "$daemon" 1; then
  echo 'short readiness budget unexpectedly passed' >&2; exit 1
fi
bash "$root/scripts/wait-emacs" test "$daemon" 8
wait "$writer"
kill "$daemon"
wait "$daemon" || true
if bash "$root/scripts/wait-emacs" test "$daemon" 8; then
  echo 'dead daemon accepted as ready' >&2; exit 1
fi
echo 'delayed initialization and dead-daemon readiness tests passed'
